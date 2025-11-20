package likelion._th.ganzithon.service;

import likelion._th.ganzithon.client.FirebaseClient;
import likelion._th.ganzithon.dto.RouteAnalysisData;
import likelion._th.ganzithon.dto.request.ReportRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CptedService {

    private final FirebaseClient firebaseClient;

    // 200m 단위
    private static final int SEGMENT_SIZE = 200;
    private static final double GRID_SIZE = 0.002;   // 200m ≒ 0.002도


    // DB에서 사용하는 gridId 계산 공식 (무조건 이거만 사용)

    private String toGridId(double lat, double lng) {
        long latIndex = (long) Math.floor(lat / GRID_SIZE);
        long lngIndex = (long) Math.floor(lng / GRID_SIZE);
        return latIndex + "_" + lngIndex;
    }

    // 경로별 CPTED 분석 (batch 조회 사용)
    public RouteAnalysisData analyzeRoute(
            String routeId,
            List<ReportRequest.Coordinate> coordinates,
            int totalDistance,
            int totalTime
    ) throws ExecutionException, InterruptedException, TimeoutException {

        if (coordinates == null || coordinates.isEmpty()) {
            return buildEmptyRouteAnalysis(routeId, totalDistance, totalTime);
        }

        long startTime = System.currentTimeMillis();

        // ✅ Step 1: 필요한 모든 gridId를 먼저 수집 (중복 제거)
        Set<String> uniqueGridIds = coordinates.stream()
                .map(point -> toGridId(point.getLat(), point.getLng()))
                .collect(Collectors.toSet());

        log.info("🔍 경로 {}: 총 좌표 {} 개 → 고유 셀 {} 개",
                routeId, coordinates.size(), uniqueGridIds.size());

        // ✅ Step 2: Batch로 한 번에 조회
        Map<String, FirebaseClient.SafetyCell> visitedCells =
                firebaseClient.getCellDataBatch(new ArrayList<>(uniqueGridIds));

        long fetchTime = System.currentTimeMillis();
        log.info("⏱️ DB 조회 완료: {} ms", fetchTime - startTime);

        // Step 3: 경로 전체 점수 계산
        double sumScoreForPath = 0.0;
        int validPointCount = 0;

        for (ReportRequest.Coordinate point : coordinates) {
            String gridId = toGridId(point.getLat(), point.getLng());
            FirebaseClient.SafetyCell cell = visitedCells.get(gridId);

            if (cell != null) {
                sumScoreForPath += cell.getCptedScore();
                validPointCount++;
            }
        }

        double avgCpted = validPointCount == 0 ? 0.0 : sumScoreForPath / validPointCount;
        avgCpted = Math.round(avgCpted * 10.0) / 10.0;

        log.info("📊 경로 {}: 유효 셀 {} 개, 평균 CPTED {}",
                routeId, visitedCells.size(), avgCpted);

        // Step 4: 시설물 개수 합산
        int totalCctv = 0, totalLight = 0, totalStore = 0,
                totalPolice = 0, totalSchool = 0;

        for (FirebaseClient.SafetyCell cell : visitedCells.values()) {
            totalCctv += cell.getCctvCount() != null ? cell.getCctvCount() : 0;
            totalLight += cell.getLightCount() != null ? cell.getLightCount() : 0;
            totalStore += cell.getStoreCount() != null ? cell.getStoreCount() : 0;
            totalPolice += cell.getPoliceCount() != null ? cell.getPoliceCount() : 0;
            totalSchool += cell.getSchoolCount() != null ? cell.getSchoolCount() : 0;
        }

        // Step 5: 200m 구간별 분석
        List<RouteAnalysisData.SegmentAnalysis> segments =
                buildSegments(coordinates, visitedCells, totalDistance);

        int riskCount = (int) segments.stream()
                .filter(s -> !s.getSafetyLevel().equals("안전"))
                .count();

        long endTime = System.currentTimeMillis();
        log.info("✅ 경로 {} 분석 완료: {} ms", routeId, endTime - startTime);

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

    // 빈 경로 데이터 반환
    private RouteAnalysisData buildEmptyRouteAnalysis(
            String routeId, int totalDistance, int totalTime) {
        return RouteAnalysisData.builder()
                .routeId(routeId)
                .distance(totalDistance)
                .time(totalTime)
                .coordinates(Collections.emptyList())
                .cctvCount(0)
                .lightCount(0)
                .storeCount(0)
                .policeCount(0)
                .schoolCount(0)
                .cptedAvg(0.0)
                .segments(Collections.emptyList())
                .riskSegmentCount(0)
                .build();
    }

    // ==================== Percentile 관련 ====================

    public int getPercentileScore(String type, int value) {
        return estimatePercentile(value, getAverageCount(type));
    }

    // Percentile 추정 (임시)
    private int estimatePercentile(int count, int avgCount) {
        if (count == 0) return 10;
        if (count >= avgCount * 3) return 90;
        if (count >= avgCount * 2) return 75;
        if (count >= avgCount) return 60;
        if (count >= avgCount / 2) return 40;
        return 25;
    }

    // 타입별 평균값 (대략적인 기준)
    private int getAverageCount(String type) {
        switch (type.toLowerCase()) {
            case "cctv":
                return 5;
            case "light":
                return 8;
            case "store":
                return 3;
            case "police":
                return 1;
            default:
                return 5;
        }
    }

    // ==================== 구간별 분석 ====================

    private List<RouteAnalysisData.SegmentAnalysis> buildSegments(
            List<ReportRequest.Coordinate> coordinates,
            Map<String, FirebaseClient.SafetyCell> visitedCells,
            Integer totalDistance
    ) {
        List<RouteAnalysisData.SegmentAnalysis> segments = new ArrayList<>();

        int numSegments = (int) Math.ceil((double) totalDistance / SEGMENT_SIZE);

        for (int i = 0; i < numSegments; i++) {
            int startDist = i * SEGMENT_SIZE;
            int endDist = Math.min((i + 1) * SEGMENT_SIZE, totalDistance);

            List<ReportRequest.Coordinate> segmentCoords = getCoordinatesInSegment(
                    coordinates,
                    startDist,
                    endDist,
                    totalDistance
            );

            SegmentStats stats = calculateSegmentStats(segmentCoords, visitedCells);

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

    private List<ReportRequest.Coordinate> getCoordinatesInSegment(
            List<ReportRequest.Coordinate> allCoords,
            int startDist,
            int endDist,
            int totalDist
    ) {
        List<ReportRequest.Coordinate> result = new ArrayList<>();
        int coordsCount = allCoords.size();

        int startIdx = (int) ((double) startDist / totalDist * coordsCount);
        int endIdx = (int) Math.ceil((double) endDist / totalDist * coordsCount);

        for (int i = startIdx; i < Math.min(endIdx, coordsCount); i++) {
            result.add(allCoords.get(i));
        }

        if (result.isEmpty() && startIdx < coordsCount) {
            return List.of(allCoords.get(startIdx));
        }

        return result;
    }

    private SegmentStats calculateSegmentStats(
            List<ReportRequest.Coordinate> segmentCoords,
            Map<String, FirebaseClient.SafetyCell> allCells
    ) {
        Set<String> segmentCellIds = new HashSet<>();
        for (ReportRequest.Coordinate coord : segmentCoords) {
            segmentCellIds.add(toGridId(coord.getLat(), coord.getLng()));
        }

        int cctv = 0, light = 0, store = 0, police = 0, school = 0;
        double totalScore = 0;
        int validCells = 0;

        for (String cellId : segmentCellIds) {
            FirebaseClient.SafetyCell cell = allCells.get(cellId);
            if (cell != null) {
                cctv += cell.getCctvCount();
                light += cell.getLightCount();
                store += cell.getStoreCount();
                police += cell.getPoliceCount();
                school += cell.getSchoolCount();
                totalScore += cell.getCptedScore();
                validCells++;
            }
        }

        double avgScore = validCells > 0 ? totalScore / validCells : 0.0;
        avgScore = Math.round(avgScore * 10.0) / 10.0;

        return new SegmentStats(avgScore, cctv, light, store, police, school);
    }

    private String getSafetyLevel(double cptedScore, int cctvCount, int lightCount) {
        int totalSafety = cctvCount + lightCount;

        if (cptedScore >= 3.0 && totalSafety >= 4) {
            return "안전";
        } else if (cptedScore >= 1.5 || totalSafety >= 2) {
            return "주의";
        } else {
            return "위험";
        }
    }

    private String generateSegmentDescription(SegmentStats stats) {
        double cctvContribution = stats.cctvCount * 0.4;
        double lightContribution = stats.lightCount * 0.3;
        double storeContribution = stats.storeCount * 0.2;
        double policeContribution = stats.policeCount * 0.1;
        double schoolContribution = stats.schoolCount * 0.1;

        Map<String, Double> contributions = new LinkedHashMap<>();
        contributions.put("CCTV", cctvContribution);
        contributions.put("가로등", lightContribution);
        contributions.put("편의점", storeContribution);
        contributions.put("경찰서", policeContribution);
        contributions.put("학교", schoolContribution);

        List<String> topFeatures = contributions.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(2)
                .filter(e -> e.getValue() > 0)
                .map(e -> formatFeature(
                        e.getKey(),
                        getActualCount(e.getKey(), stats),
                        e.getValue()))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        List<String> lackingFeatures = new ArrayList<>();
        if (stats.cctvCount == 0 && cctvContribution == 0) {
            lackingFeatures.add("CCTV 없음");
        }
        if (stats.lightCount == 0) {
            lackingFeatures.add("조명 부족");
        } else if (stats.lightCount <= 2) {
            lackingFeatures.add("조금 어두운 편");
        }

        List<String> description = new ArrayList<>();
        description.addAll(topFeatures);
        if (!lackingFeatures.isEmpty()) {
            description.add(lackingFeatures.get(0));
        }

        return description.isEmpty() ? "일반 도로" : String.join(", ", description);
    }

    private String formatFeature(String name, int count, double contribution) {
        if (count >= 8) return name + " 매우 많음";
        if (count >= 4) return name + " 충분함";
        if (count >= 2) return name + " 어느 정도 있음";
        if (count == 1) return name + " 소수 존재";
        return "";
    }

    private int getActualCount(String feature, SegmentStats stats) {
        switch (feature) {
            case "CCTV":
                return stats.cctvCount;
            case "가로등":
                return stats.lightCount;
            case "편의점":
                return stats.storeCount;
            case "경찰서":
                return stats.policeCount;
            case "학교":
                return stats.schoolCount;
            default:
                return 0;
        }
    }

    private static class SegmentStats {
        double avgScore;
        int cctvCount;
        int lightCount;
        int storeCount;
        int policeCount;
        int schoolCount;

        SegmentStats(
                double avgScore,
                int cctvCount,
                int lightCount,
                int storeCount,
                int policeCount,
                int schoolCount
        ) {
            this.avgScore = avgScore;
            this.cctvCount = cctvCount;
            this.lightCount = lightCount;
            this.storeCount = storeCount;
            this.policeCount = policeCount;
            this.schoolCount = schoolCount;
        }
    }
}
