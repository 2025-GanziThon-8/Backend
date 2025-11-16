package likelion._th.ganzithon.dto;

import likelion._th.ganzithon.domain.LatLng;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteAnalysisData {

    // 기본 경로 정보
    private String routeId;              // 경로 ID (예: "route-1")
    private Integer distance;            // 거리 (미터)
    private Integer time;                // 시간 (초)
    private List<LatLng> coordinates;    // 좌표 목록

    // CPTED 분석 결과 (전체 경로)
    private Double cptedAvg;             // CPTED 평균 점수 (0.0 ~ 5.0)
    private Integer cctvCount;           // 전체 CCTV 개수
    private Integer lightCount;          // 전체 가로등 개수
    private Integer storeCount;          // 전체 편의시설 개수
    private Integer policeCount;         // 전체 경찰서 개수
    private Integer schoolCount;         // 전체 학교 개수

    // 구간별 분석 결과
    private List<SegmentAnalysis> segments;  // 200m 단위 구간 분석
    private Integer riskSegmentCount;        // 주의/위험 구간 개수

    // 구간 분석 용
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentAnalysis {
        private Integer startDistance;   // 구간 시작 거리 (예: 0)
        private Integer endDistance;     // 구간 종료 거리 (예: 200)
        private Double cptedScore;       // 이 구간의 CPTED 평균 점수
        private String description;      // 구간 설명 (예: "조명 밝음, CCTV 다수")
        private String safetyLevel;      // 안전도 ("안전", "주의", "위험")
        private Integer cctvCount;       // 이 구간의 CCTV 개수
        private Integer lightCount;      // 이 구간의 가로등 개수
    }
}
