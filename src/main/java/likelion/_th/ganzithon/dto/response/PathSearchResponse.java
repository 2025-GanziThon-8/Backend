package likelion._th.ganzithon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
// 길찾기 응답
public class PathSearchResponse {
    private String message;
    private List<PathInfo> paths;
}
