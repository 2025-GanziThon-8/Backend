package likelion._th.ganzithon.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
// 길찾기 - 길에 대한 정보
public class PathInfo {
    // 경로 ID (path-1, path-2, path-3)
    private String id;
    // 걸리는 시간 (s)
    private Integer time;
    // 거리 (m)
    private Integer distance;
    private String polyline;
    // CPTED 평균 점수
    private Map<String, Double> cpted;
    // 종합 등금: A(90점)
    @JsonProperty("summary_grade")
    private String summaryGrade;
    // AI 리뷰
    @JsonProperty("ai_preview")
    private List<String> aiPreview;
    // 추천 경로 여부
    @JsonProperty("is_recommended")
    private boolean isRecommended;
}
