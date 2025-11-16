package likelion._th.ganzithon.service;

import likelion._th.ganzithon.client.TmapsClient;
import likelion._th.ganzithon.client.UpstageAiClient;
import likelion._th.ganzithon.dto.RouteAnalysisData;
import likelion._th.ganzithon.dto.request.PathSearchRequest;
import likelion._th.ganzithon.dto.response.PathInfo;
import likelion._th.ganzithon.dto.response.PathSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
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
    public PathSearchResponse searchPaths(PathSearchRequest request) throws ExecutionException, InterruptedException, TimeoutException {
        log.info("경로 검색 시작: ({},{}) → ({},{})",
                request.getStartLat(), request.getStartLng(),
                request.getEndLat(), request.getEndLng());

        // 경유지 로깅
        if (request.hasWaypoint()) {
            log.info("경유지 포함: ({},{})", request.getWaypointLat(), request.getWaypointLng());
        }

        // 1. 구글 api로 경로 조회
        List<TmapsClient.TmapRoute> tmapRoutes = tmapsClient.getRoutes(request.getStartLat(),
                request.getStartLng(),
                request.getEndLat(),
                request.getEndLng(),
                request.getWaypointLat(),  // null 가능
                request.getWaypointLng()   // null 가능
        );

        if (tmapRoutes.isEmpty()) {
            throw new IllegalArgumentException("경로를 찾을 수 없습니다. 출발지와 도착지를 확인해주세요.");
        }

        log.info("티맵에서 {} 개 경로 수신", tmapRoutes.size());

        //2. 각 경로에 대해 CPTED 분석 수행
        List<RouteAnalysisData> analyzedRoutes = new ArrayList<>();
        List<String> enncodedPolylines = new ArrayList<>();

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
            enncodedPolylines.add(tmapRoute.getEncodedPolyline()); // 원본 저장
        }

        // 3. 3개의 경로 선택
        List<RouteAnalysisData> selectedRoutes = selectThreeRoutes(analyzedRoutes);

        // 4. AI 추천 경로 선택
        String recommendedRouteId = upstageAiClient.selectRecommendedRoute(selectedRoutes);

        // 5. PathInfo로 변환
        List<PathInfo> pathInfos = new ArrayList<>();
        for(RouteAnalysisData route : selectedRoutes) {
            int originalIndex = analyzedRoutes.indexOf((route));
            String encodedPolyline = enncodedPolylines.get(originalIndex);
            boolean isRecommended = route.getRouteId().equals(recommendedRouteId);

            pathInfos.add(convertToPathInfo(route, isRecommended, encodedPolyline));
        }

        log.info("경로 검색 완료: 총 {} 개 경로 반환 (추천: {})", pathInfos.size(), recommendedRouteId);

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

        // 2. 일반/빠른 경로
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

        // 3. 중간 경로
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

    // ReportResponse -> PathInfo 변환
    private PathInfo convertToPathInfo(
            RouteAnalysisData route, boolean isRecommended, String encodedPolyline) {
        // 종합 등급 계산
        String summaryGrade = calculateGrade(route.getCptedAvg());

        // AI 프리뷰 설정
        List<String> aiPreview = upstageAiClient.generateRoutePreview(route);

        return PathInfo.builder()
                .id(route.getRouteId())
                .time(route.getTime())
                .distance(route.getDistance())
                .polyline(encodedPolyline)
                .cpted(Map.of("avg", route.getCptedAvg()))
                .summaryGrade(summaryGrade)
                .aiPreview(aiPreview)
                .isRecommended(isRecommended)
                .build();
    }

    // CPTED 등급
    private String calculateGrade(double cptedAvg) {
        String grade;
        if (cptedAvg >= 4.0) grade = "A";
        else if (cptedAvg >= 3.0) grade = "B";
        else if (cptedAvg >= 2.0) grade = "C";
        else if (cptedAvg >= 1.0) grade = "D";
        else grade = "F";

        int scaledScore = (int) Math.min(cptedAvg * 20, 100);
        return String.format("%s (%d점)", grade, scaledScore);
    }

}
