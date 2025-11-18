package likelion._th.ganzithon.service;

import likelion._th.ganzithon.client.FirebaseClient;
import likelion._th.ganzithon.dto.RouteAnalysisData;
import likelion._th.ganzithon.dto.request.ReportRequest;
import likelion._th.ganzithon.util.PercentileCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class CptedService {
    private final FirebaseClient firebaseClient;
    private static final int SEGMENT_SIZE = 200; // db 나눈 구간의 크기
    private PercentileCalculator percentileCalc;

    public RouteAnalysisData analyzeRoute(
            String routeId, List<ReportRequest.Coordinate> coordinates, int totalDistance, int totalTime
    ) throws ExecutionException, InterruptedException, TimeoutException {
        // 1. 경로상의 모든 격자 셀 데이터 조회
        Map<String, FirebaseClient.SafetyCell> visitedCells = new HashMap<>();

        for (ReportRequest.Coordinate point : coordinates) {
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

        // 경로상의 셀 조회 -> 셀의 데이터 더함
        for (FirebaseClient.SafetyCell cell : visitedCells.values()) {
            if (cell==null) continue;

            totalCctv += cell.getCctvCount();
            totalLight += cell.getLightCount();
            totalStore += cell.getStoreCount();
            totalPolice += cell.getPoliceCount();
            totalSchool += cell.getSchoolCount();
            totalScore += cell.getCptedScore();
        }
        // 평균 cpted 값
        // 경로상 지나가는 cell의 cptedSocre을 평균낸 값
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

    public int getPercentileScore(String type, int value) {
        if (percentileCalc != null) {
            return percentileCalc.getPercentile(type, value);
        } else {
            // Percentile 데이터 없으면 간단한 추정 사용
            return estimatePercentile(value, getAverageCount(type));
        }    }

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
        switch(type.toLowerCase()) {
            case "cctv": return 5;
            case "light": return 8;
            case "store": return 3;
            case "police": return 1;
            default: return 5;
        }
    }

    // 200m 구간별 분석
    private List<RouteAnalysisData.SegmentAnalysis> buildSegments(
        List<ReportRequest.Coordinate> coordinates,
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
            List<ReportRequest.Coordinate> segmentCoords = getCoordinatesInSegment(
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
    private List<ReportRequest.Coordinate> getCoordinatesInSegment(
            List<ReportRequest.Coordinate> allCoords,
            int startDist,
            int endDist,
            int totalDist
    ) {
        List<ReportRequest.Coordinate> result = new ArrayList<>();
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
            List<ReportRequest.Coordinate> segmentCoords,
            Map<String, FirebaseClient.SafetyCell> allCells
    ) {
        Set<String> segmentCellIds = new HashSet<>();
        for (ReportRequest.Coordinate coord : segmentCoords) {
            segmentCellIds.add(getCellId(coord.getLat(), coord.getLng()));
        }

        int cctv = 0, light = 0, store = 0, police = 0, school = 0;
        double totalScore = 0;
        int validCells = 0;

        // 해당 구간 셀의 cctv,light,store,police,school 개수 더하기
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

        // 평균 점수 구하기
        double avgScore = validCells > 0 ? totalScore / validCells : 0.0;
        avgScore = Math.round(avgScore * 10.0) / 10.0;

        return new SegmentStats(avgScore, cctv, light, store, police, school);
    }

    // 구간별 안정도 판정: 0m ~ 200m : 조명 밝음, CCTV 다수 → 안정
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

    // 구간 설명 생성: 0m ~ 200m : 조명 밝음, CCTV 다수 → 안정
    private String generateSegmentDescription(SegmentStats stats) {
        // 1. 각 시설의 기여도 계산(가중치)
        double cctvContribution = stats.cctvCount * 0.4;
        double lightContribution = stats.lightCount * 0.3;
        double storeContribution = stats.storeCount * 0.2;
        double policeContribution = stats.policeCount * 0.1;
        double schoolContribution = stats.schoolCount * 0.1;

        // 2. 가장 높은 기여도 2개 선택
        Map<String, Double> contributions = new LinkedHashMap<>();
        contributions.put("CCTV", cctvContribution);
        contributions.put("가로등", lightContribution);
        contributions.put("편의점", storeContribution);
        contributions.put("경찰서", policeContribution);

        List<String> topFeatures = contributions.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(2)
                .filter(e -> e.getValue() > 0)
                .map(e -> formatFeature(e.getKey(),
                        getActualCount(e.getKey(), stats),
                        e.getValue()))
                .collect(Collectors.toList());

        // 3. 부족한 요소도 언급 (중요도 높은 것 우선)
        List<String> lackingFeatures = new ArrayList<>();
        if (stats.cctvCount == 0 && cctvContribution == 0) {
            lackingFeatures.add("CCTV 없음");
        }
        if (stats.lightCount <= 1) {
            lackingFeatures.add("조명 부족");
        }

        // 4. 설명 조합
        List<String> description = new ArrayList<>();
        description.addAll(topFeatures);
        if (!lackingFeatures.isEmpty()) {
            description.add(lackingFeatures.get(0)); // 가장 심각한 것 1개만
        }

        return description.isEmpty() ? "일반 도로" : String.join(", ", description);
    }

    private String formatFeature(String name, int count, double contribution) {
        if (count >= 5) return name + " 다수";
        if (count >= 2) return name + " 있음";
        if (count == 1) return name + " 소수";
        return "";
    }

    private int getActualCount(String feature, SegmentStats stats) {
        switch(feature) {
            case "CCTV": return stats.cctvCount;
            case "가로등": return stats.lightCount;
            case "편의점": return stats.storeCount;
            case "경찰서": return stats.policeCount;
            default: return 0;
        }
    }

    //격자 ID 생성 (200m 단위 → 0.002도)
    //위도/경도를 5000배 → 소수점 5자리까지 사용
    private String getCellId(double lat, double lng) {
        final double GRID_CELL_SIZE = 0.002;

        int latKey = (int) Math.floor(lat / GRID_CELL_SIZE);
        int lngKey = (int) Math.floor(lng / GRID_CELL_SIZE);

        return latKey + "_" + lngKey;
    }

    // 구간 통계 내부 클래스
    private static class SegmentStats {
        double avgScore;
        int cctvCount;
        int lightCount;
        int storeCount;
        int policeCount;
        int schoolCount;

        SegmentStats(
                double avgScore, int cctvCount, int lightCount,
                int storeCount, int policeCount, int schoolCount) {
            this.avgScore = avgScore;
            this.cctvCount = cctvCount;
            this.lightCount = lightCount;
            this.storeCount = storeCount;
            this.policeCount = policeCount;
            this.schoolCount = schoolCount;
        }
    }
}
