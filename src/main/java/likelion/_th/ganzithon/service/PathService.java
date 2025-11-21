package likelion._th.ganzithon.service;

import likelion._th.ganzithon.client.TmapsClient;
import likelion._th.ganzithon.client.UpstageAiClient;
import likelion._th.ganzithon.dto.RouteAnalysisData;
import likelion._th.ganzithon.dto.request.PathSearchRequest;
import likelion._th.ganzithon.dto.request.ReportRequest;
import likelion._th.ganzithon.dto.response.PathInfo;
import likelion._th.ganzithon.dto.response.PathSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
// 경로 조회
public class PathService {

    //    private final GoogleMapsClient googleMapsClient;
    private final TmapsClient tmapsClient;
    private final UpstageAiClient upstageAiClient;
    private final CptedService cptedService;

    // 선택할 3개의 경로를 탐색
    public PathSearchResponse searchPaths(PathSearchRequest request)
            throws ExecutionException, InterruptedException, TimeoutException {

        // 전체 시작 시간
        long totalStartTime = System.currentTimeMillis();

        log.info("경로 검색 시작: ({},{}) → ({},{})",
                request.getStartLat(), request.getStartLng(),
                request.getEndLat(), request.getEndLng());

        // 경유지 로깅
        if (request.hasWaypoint()) {
            log.info("경유지 포함: ({},{})", request.getWaypointLat(), request.getWaypointLng());
        }

        // 1. 티맵 api로 경로 조회
        // 티맵 호출 시작 시간
        long tmapStartTime = System.currentTimeMillis();
        List<TmapsClient.TmapRoute> tmapRoutes = tmapsClient.getRoutes(
                request.getStartLat(),
                request.getStartLng(),
                request.getEndLat(),
                request.getEndLng(),
                request.getWaypointLat(),  // null 가능
                request.getWaypointLng()   // null 가능
        );

        if (tmapRoutes.isEmpty()) {
            throw new IllegalArgumentException("경로를 찾을 수 없습니다. 출발지와 도착지를 확인해주세요.");
        }

        // 티맵 호출 완료 시간
        long tmapEndTime = System.currentTimeMillis();
        log.info("티맵에서 {} 개 경로 수신", tmapRoutes.size());

        // 2. 각 경로에 대해 CPTED 분석 수행
        // cpted 분석 시작 시간
        long cptedStartTime = System.currentTimeMillis();

        List<RouteAnalysisData> analyzedRoutes = new ArrayList<>();
        List<List<ReportRequest.Coordinate>> polylines = new ArrayList<>();

        for (int i = 0; i < tmapRoutes.size(); i++) {
            TmapsClient.TmapRoute tmapRoute = tmapRoutes.get(i);
            String routeId = "path-" + (i + 1);

            RouteAnalysisData analyzed = cptedService.analyzeRoute(
                    routeId,
                    tmapRoute.getCoordinates(),
                    tmapRoute.getDistance(),
                    tmapRoute.getDuration()
            );

            analyzedRoutes.add(analyzed);
            // 원본 폴리라인 좌표 저장
            polylines.add(tmapRoute.getEncodedPolyline());
        }
        // cpted 분석 완료 시간
        long cptedEndTime = System.currentTimeMillis();

        // 3. 3개의 경로 선택
        List<RouteAnalysisData> selectedRoutes = selectThreeRoutes(analyzedRoutes);

        // 점수 계산용 최소 거리/시간 (선택된 3개 경로 기준)
        int minDistance = selectedRoutes.stream()
                .mapToInt(RouteAnalysisData::getDistance)
                .min()
                .orElse(1);

        int minTime = selectedRoutes.stream()
                .mapToInt(RouteAnalysisData::getTime)
                .min()
                .orElse(1);

        // ai 프리뷰 생성 시작 시간
        long aiStartTime = System.currentTimeMillis();

        // 각 경로에 대한 AI 프리뷰를 병렬로 시작
        Map<String, CompletableFuture<List<String>>> previewFutures = new HashMap<>();
        for (RouteAnalysisData route : selectedRoutes) {
            previewFutures.put(
                    route.getRouteId(),
                    upstageAiClient.generateRoutePreviewAsync(route)
            );
        }

        // 4. AI 추천 경로 선택
        String recommendedRouteId = upstageAiClient.selectRecommendedRoute(selectedRoutes);

        // 5. PathInfo로 변환
        List<PathInfo> pathInfos = new ArrayList<>();
        for (RouteAnalysisData route : selectedRoutes) {
            int originalIndex = analyzedRoutes.indexOf(route);
            List<ReportRequest.Coordinate> encodedPolyline = polylines.get(originalIndex);
            boolean isRecommended = route.getRouteId().equals(recommendedRouteId);

            // 병렬로 돌려놓은 프리뷰 결과 받기 (타임아웃은 적당히 설정)
            List<String> aiPreview = previewFutures.get(route.getRouteId())
                    .join();

            pathInfos.add(
                    convertToPathInfo(
                            route,
                            isRecommended,
                            encodedPolyline,
                            minDistance,
                            minTime,
                            aiPreview
                    )
            );
        }

        long aiEndTime = System.currentTimeMillis();
        log.info("🤖 AI 분석 완료: 추천 경로 {} ({}ms)",
                recommendedRouteId, aiEndTime - aiStartTime);

        // ⏱️ 전체 종료 시간
        long totalEndTime = System.currentTimeMillis();
        long totalElapsed = totalEndTime - totalStartTime;

        log.info("✅ 경로 검색 완료: 총 {} 개 경로 반환 (추천: {}) | ⏱️ 전체 소요시간: {}ms (티맵: {}ms, CPTED: {}ms, AI: {}ms)",
                pathInfos.size(),
                recommendedRouteId,
                totalElapsed,
                tmapEndTime - tmapStartTime,
                cptedEndTime - cptedStartTime,
                aiEndTime - aiStartTime);

        return PathSearchResponse.builder()
                .message("후보 경로 조회 성공")
                .paths(pathInfos)
                .build();
    }

    // 3개의 경로 선택 (1. 안전(CPTED 최고점) 2. 일반/빠른 경로 3. 중간 경로(나머지)
    private List<RouteAnalysisData> selectThreeRoutes(List<RouteAnalysisData> allRoutes) {
        if (allRoutes.size() <= 3) {
            return allRoutes;
        }

        List<RouteAnalysisData> selected = new ArrayList<>();

        // 1. 안전 경로: CPTED 최고점
        RouteAnalysisData safest = allRoutes.stream()
                .max(Comparator.comparingDouble(RouteAnalysisData::getCptedAvg))
                .orElse(allRoutes.get(0));
        selected.add(safest);

        // 2. 일반/빠른 경로: 거리 최단
        RouteAnalysisData fastest = allRoutes.stream()
                .min(Comparator.comparingInt(RouteAnalysisData::getDistance))
                .orElse(allRoutes.get(2));

        if (!fastest.getRouteId().equals(safest.getRouteId())) {
            selected.add(fastest);
        } else {
            // 거리 최단이 안전 최고와 같으면 두 번째로 짧은 경로
            selected.add(allRoutes.stream()
                    .filter(r -> !r.getRouteId().equals(safest.getRouteId()))
                    .min(Comparator.comparingInt(RouteAnalysisData::getDistance))
                    .orElse(allRoutes.get(1)));
        }

        // 3. 중간 경로: 안전도 & 거리 균형
        RouteAnalysisData balanced = allRoutes.stream()
                .filter(r -> !selected.contains(r))
                .max(Comparator.comparingDouble(r ->
                        r.getCptedAvg() * 0.6 - (r.getDistance() / 1000.0) * 0.4)) // 안전도 60%, 거리 40%
                .orElse(allRoutes.stream()
                        .filter(r -> !selected.contains(r))
                        .findFirst()
                        .orElse(allRoutes.get(2)));
        selected.add(balanced);

        log.info("3개 경로 선택 완료: 안전우선={}, 빠른경로={}, 균형경로={}",
                safest.getRouteId(), fastest.getRouteId(), balanced.getRouteId());

        return selected;
    }

    // RouteAnalysisData -> PathInfo 변환
    private PathInfo convertToPathInfo(
            RouteAnalysisData route,
            boolean isRecommended,
            List<ReportRequest.Coordinate> encodedPolyline,
            int minDistance,
            int minTime,
            List<String> aiPreview
    ) {
        // 최종 점수 계산 (0~100)
        double score = calcRouteScore(route, minDistance, minTime);
        String summaryGrade = toSummaryGrade(score);

        log.info("경로 {} 점수 계산: cptedAvg={}, distance={}, time={}, score={}",
                route.getRouteId(), route.getCptedAvg(),
                route.getDistance(), route.getTime(), score);

        return PathInfo.builder()
                .id(route.getRouteId())
                .time(route.getTime())
                .distance(route.getDistance())
                .polyline(encodedPolyline)
                .cpted(Map.of("avg", route.getCptedAvg()))
                .summaryGrade(summaryGrade)
                .aiPreview(aiPreview)        // 전달받은 값 그대로 사용
                .isRecommended(isRecommended)
                .build();
    }

    // ================== 점수/등급 계산 유틸 ==================

    /**
     * 경로별 최종 점수 0~100 계산
     * - 70점: CPTED 평균 (0~30점 → 0~70점 스케일링)
     * - 20점: 거리 (선택된 경로 중 가장 짧은 거리 = 20점)
     * - 10점: 시간 (선택된 경로 중 가장 짧은 시간 = 10점)
     */
    private double calcRouteScore(RouteAnalysisData r, int minDistance, int minTime) {
        // 1) 안전도 점수 (CPTED 평균 0~30 → 0~70)
        double safetyScore = 0.0;
        if (r.getCptedAvg() > 0) {
            safetyScore = Math.min(r.getCptedAvg(), 30.0) / 30.0 * 70.0;
        }

        // 2) 거리 점수 (가장 짧은 거리 = 20점)
        double distanceScore = 0.0;
        if (minDistance > 0 && r.getDistance() > 0) {
            distanceScore = 20.0 * ((double) minDistance / r.getDistance());
            if (distanceScore > 20.0) distanceScore = 20.0;
        }

        // 3) 시간 점수 (가장 짧은 시간 = 10점)
        double timeScore = 0.0;
        if (minTime > 0 && r.getTime() > 0) {
            timeScore = 10.0 * ((double) minTime / r.getTime());
            if (timeScore > 10.0) timeScore = 10.0;
        }

        double total = safetyScore + distanceScore + timeScore;

        // 0~100 사이로 클램프
        if (total < 0) total = 0;
        if (total > 100) total = 100;

        return Math.round(total * 10.0) / 10.0; // 소수 1자리
    }

    /**
     * 점수 → 등급 문자열 변환
     * 예) 96.3 → "A (96점)"
     */
    private String toSummaryGrade(double score) {
        String grade;
        if (score >= 90) {
            grade = "A";
        } else if (score >= 75) {
            grade = "B";
        } else if (score >= 60) {
            grade = "C";
        } else if (score >= 40) {
            grade = "D";
        } else {
            grade = "E";
        }
        int roundedScore = (int) Math.round(score);
        return String.format("%s (%d점)", grade, roundedScore);
    }

}
