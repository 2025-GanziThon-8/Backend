package likelion._th.ganzithon.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import likelion._th.ganzithon.domain.LatLng;
import lombok.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// Ai 리포트 응답
public class ReportResponse {
    private String message; // "CPTED 리포트 생성 성공"

    private ReportDetail report;

    // 리포트 상세 내용
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportDetail {

        // 1. 경로 기본 정보
        @JsonProperty("route_summary")
        private RouteSummary routeSummary;

        // 2. CPTED 5대 평가
        @JsonProperty("cpted_evaluation")
        private CptedEvaluation cptedEvaluation;

        // 3. 구간별 안내
        @JsonProperty("segment_guides")
        private List<SegmentGuide> segmentGuides;

        // 4. AI 종합 분석 (3-4줄)
        @JsonProperty("ai_summary")
        private String aiSummary;

        @JsonProperty("created_at")
        private LocalDateTime createdAt;
    }

    // 경로 요약 정보
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteSummary {
        private String origin;          // 출발지 (예: "성수역")
        private String destination;     // 도착지 (예: "한양대역")

        @JsonProperty("total_distance")
        private Integer totalDistance;  // 거리 (미터)

        @JsonProperty("total_time")
        private Integer totalTime;      // 시간 (초)

        @JsonProperty("overall_grade")
        private String overallGrade;    // 종합 등급 (예: "A (82점)")

        private Integer score;
        private String grade;
    }

    // CPTED 5대 평가
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CptedEvaluation {

        @JsonProperty("natural_surveillance")
        private CptedItem naturalSurveillance;  // 자연감시

        @JsonProperty("access_control")
        private CptedItem accessControl;        // 접근통제

        @JsonProperty("territoriality")
        private CptedItem territoriality;       // 영역성 강화

        @JsonProperty("activity_support")
        private CptedItem activitySupport;      // 활동성 증대

        @JsonProperty("maintenance")
        private CptedItem maintenance;          // 유지관리

        // 시설물 개수
        @JsonProperty("facilities")
        private Facilities facilities;
    }

    // CPTED 개별 항목
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CptedItem {
        private String name;        // 항목명 (예: "자연감시")
        private Integer score;      // 점수 (0-100)
        private String description; // 설명 (예: "밝고 CCTV 다수 존재")
    }

    // 시설물 집계
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Facilities {
        @JsonProperty("cctv_count")
        private Integer cctvCount;

        @JsonProperty("light_count")
        private Integer lightCount;

        @JsonProperty("store_count")
        private Integer storeCount;

        @JsonProperty("police_count")
        private Integer policeCount;

        @JsonProperty("school_count")
        private Integer schoolCount;
    }

    // 구간별 안내
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentGuide {

        @JsonProperty("distance_range")
        private String distanceRange;   // 구간 거리 (예: "0~200m")

        private String description;     // 구간 설명 (예: "조명 밝음, CCTV 다수")

        @JsonProperty("safety_level")
        private String safetyLevel;     // 안전도 (안전/주의/위험)

        private List<String> recommendations; // 추천 사항
    }
}
