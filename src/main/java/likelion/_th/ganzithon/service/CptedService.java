package likelion._th.ganzithon.service;

import likelion._th.ganzithon.client.FirebaseClient;
import likelion._th.ganzithon.domain.LatLng;
import likelion._th.ganzithon.dto.RouteAnalysisData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class CptedService {
    private final FirebaseClient firebaseClient;
    private static final int SEGMENT_SIZE = 200; // db 나눈 구간의 크기

    public RouteAnalysisData analyzeRoute(
            String routeId, List<LatLng> coordinates, int totalDistance, int totalTime
    ) throws ExecutionException, InterruptedException, TimeoutException {
        // 1. 경로상의 모든 격자 셀 데이터 조회
        Map<String, FirebaseClient.SafetyCell> visitedCells = new HashMap<>();

        for (LatLng point : coordinates) {
            String cellId = getCellId(point.getLat(), point.getLng());

            if (!visitedCells.containsKey(cellId)) {
                // db에서 해당 id의 데이터 가져옴
                FirebaseClient.SafetyCell cell = firebaseClient.getCellData(cellId);
                if (cell != null) {
                    visitedCells.put(cellId, cell);
                }
            }
        }

        // 2. 전체 경로 CPTED 합산
        int totalCctv = 0, totalLight = 0, totalStore = 0, totalPolice = 0, totalSchool = 0;
        double totalScore = 0;

        for (FirebaseClient.SafetyCell cell : visitedCells.values()) {
            if (cell==null) continue;

            totalCctv += cell.getCctvCount();
            totalLight += cell.getLightCount();
            totalScore += cell.getStoreCount();
            totalPolice += cell.getPoliceCount();
            totalSchool += cell.getSchoolCount();
            totalScore += cell.getCptedScore();
        }
        double avgCpted = visitedCells.isEmpty() ? 0.0 : totalScore / visitedCells.size();
        avgCpted = Math.round(avgCpted * 10.0) / 10.0;

        // 3. 200m 구간 별 분석
        List<RouteAnalysisData.SegmentAnalysis> segments =
                buildSegments(coordinates, visitedCells, totalDistance);

        // 4. 위험 구간 개수 계산
        int riskCount = (int) segments.stream()
                .filter(s -> !s.getSafetyLevel().equals("안전"))
                .count();

        // 5. 결과 반환
        return RouteAnalysisData.builder()
                .routeId(routeId)
                .distance(totalDistance)
                .time(totalTime)
                .coordinates(coordinates)
                .cctvCount(totalCctv)
                .lightCount(totalLight)
                .storeCount(totalStore)
                .policeCount(totalPolice)
                .schoolCount(totalSchool)
                .cptedAvg(avgCpted)
                .segments(segments)
                .riskSegmentCount(riskCount)
                .build();
    }


    // 200m 구간별 분석
    private List<RouteAnalysisData.SegmentAnalysis> buildSegments(
        List<LatLng> coordinates,
        Map<String, FirebaseClient.SafetyCell> visitedCells,
        Integer totalDistance
    ) {
        List<RouteAnalysisData.SegmentAnalysis> segments = new ArrayList<>();

        // 전체 거리를 200m씩 나눔
        int numSegments = (int) Math.ceil((double) totalDistance/SEGMENT_SIZE);

        for (int i = 0; i < numSegments; i++) {
            int startDist = i*SEGMENT_SIZE;
            int endDist = Math.min((i + 1) * SEGMENT_SIZE, totalDistance);

            // 이 구간에 해당하는 좌표들을 추출
            List<LatLng> segmentCoords = getCoordinatesInSegment(
                    coordinates,
                    startDist,
                    endDist,
                    totalDistance
            );

            // 이 구간의 CPTED 점수 및 시설물 계산
            SegmentStats stats = calculateSegmentStats(segmentCoords, visitedCells);

            // 안전도 및 설명 생성
            String safetyLevel = getSafetyLevel(stats.avgScore, stats.cctvCount, stats.lightCount);
            String description = generateSegmentDescription(stats);

            segments.add(RouteAnalysisData.SegmentAnalysis.builder()
                    .startDistance(startDist)
                    .endDistance(endDist)
                    .cptedScore(stats.avgScore)
                    .description(description)
                    .safetyLevel(safetyLevel)
                    .cctvCount(stats.cctvCount)
                    .lightCount(stats.lightCount)
                    .build());
        }

        return segments;
    }

    // 구간에 해당하는 좌표 추출
    private List<LatLng> getCoordinatesInSegment(
            List<LatLng> allCoords,
            int startDist,
            int endDist,
            int totalDist
    ) {
        List<LatLng> result = new ArrayList<>();
        int coordsCount = allCoords.size();

        // 거리 비율로 좌표 인덱스 계산
        int startIdx = (int) ((double) startDist / totalDist * coordsCount);
        int endIdx = (int) Math.ceil((double) endDist / totalDist * coordsCount);

        for (int i = startIdx; i < Math.min(endIdx, coordsCount); i++) {
            result.add(allCoords.get(i));
        }

        return result.isEmpty() ? List.of(allCoords.get(startIdx)) : result;
    }

    // 구간 통계 계산
    private SegmentStats calculateSegmentStats(
            List<LatLng> segmentCoords,
            Map<String, FirebaseClient.SafetyCell> allCells
    ) {
        Set<String> segmentCellIds = new HashSet<>();
        for (LatLng coord : segmentCoords) {
            segmentCellIds.add(getCellId(coord.getLat(), coord.getLng()));
        }

        int cctv = 0, light = 0;
        double totalScore = 0;
        int validCells = 0;

        for (String cellId : segmentCellIds) {
            FirebaseClient.SafetyCell cell = allCells.get(cellId);
            if (cell != null) {
                cctv += cell.getCctvCount();
                light += cell.getLightCount();
                totalScore += cell.getCptedScore();
                validCells++;
            }
        }

        double avgScore = validCells > 0 ? totalScore / validCells : 0.0;
        avgScore = Math.round(avgScore * 10.0) / 10.0;

        return new SegmentStats(avgScore, cctv, light);
    }

    // 안정도 판정
    private String getSafetyLevel(double cptedScore, int cctvCount, int lightCount) {
        int totalSafety = cctvCount + lightCount;

        if (cptedScore >= 3.5 && totalSafety >= 5) {
            return "안전";
        } else if (cptedScore >= 2.0 || totalSafety >= 2) {
            return "주의";
        } else {
            return "위험";
        }
    }

    // 구간 설명 생성
    private String generateSegmentDescription(SegmentStats stats) {
        List<String> parts = new ArrayList<>();

        if (stats.lightCount >= 3) {
            parts.add("조명 밝음");
        } else if (stats.lightCount <= 1) {
            parts.add("조명 부족");
        }

        if (stats.cctvCount >= 2) {
            parts.add("CCTV 다수");
        } else if (stats.cctvCount == 0) {
            parts.add("CCTV 없음");
        }

        if (stats.avgScore < 2.0) {
            parts.add("인적 드묾");
        }

        return parts.isEmpty() ? "일반 도로" : String.join(", ", parts);
    }


    //격자 ID 생성 (200m 단위 → 0.002도)
    //위도/경도를 5000배 → 소수점 5자리까지 사용
    private String getCellId(double lat, double lng) {
        double cellLat = Math.floor(lat * 5000) / 5000;
        double cellLng = Math.floor(lng * 5000) / 5000;
        return String.format("%.5f_%.5f", cellLat, cellLng);
    }

    // 구간 통계 내부 클래스
    private static class SegmentStats {
        double avgScore;
        int cctvCount;
        int lightCount;

        SegmentStats(double avgScore, int cctvCount, int lightCount) {
            this.avgScore = avgScore;
            this.cctvCount = cctvCount;
            this.lightCount = lightCount;
        }
    }
}
