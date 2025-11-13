package likelion._th.ganzithon.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
// 길찾기 - 길에 대한 정보
public class PathInfo {
    private String id;
    // 걸리는 시간
    private int time;
    // 거리
    private int distance;
    private String polyline;
    private Map<String, Double> cpted;
    private String summary_grade;
    private List<String> ai_preview;
    private boolean is_recommended;
}
