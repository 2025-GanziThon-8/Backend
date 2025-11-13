package likelion._th.ganzithon.client;

import likelion._th.ganzithon.dto.request.PathSearchRequest;
import likelion._th.ganzithon.dto.response.PathInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TMapClient {
    private final WebClient tmapClient;

    public List<PathInfo> getTMapApiPaths(PathSearchRequest request) {

        // TMap API에 보낼 데이터
        Map<String, Object> payload = Map.of( // 모두 필수 값
                "version", "1",
                "startLat", request.getStartLat(),
                "startLng", request.getStartLng(),
                "endLat", request.getEndLat(),
                "endLng", request.getEndLng(),
                "startName", request.getStartName(),
                "endName", request.getEndName(),
                // 요청.응답 좌표계 유형 (기본)
                "reqCoordType", "WGS84GEO",
                "resCoordType", "WGS84GEO",
                // 경로 탐색 옵션
                // 0: 추천(기본값), 4: 추천+대로우선, 10: 최단, 30: 최단+계단 제외
                "searchOption", 0
        );

        // TMap API 호출
        Mono<Map> responseMono = tmapClient.post()
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class);

        Map<String, Object> responseMap = responseMono.block();

        List<PathInfo> pathList = new ArrayList<>();
        List<Map<String, Object>> features = (List<Map<String, Object>>) responseMap.get("features");

        int idx = 1;
        for (Map<String, Object> feature : features) {
            Map<String, Object> properties = (Map<String, Object>) feature.get("properties");
            PathInfo path = PathInfo.builder()
                    .id("path-" + idx++)
                    .time((int) Double.parseDouble(properties.get("totalTime").toString()))
                    .distance((int)Double.parseDouble(properties.get("totalDistance").toString()))
                    .polyline(properties.get("geometry").toString())
                    .cpted(Map.of("avg", 0.0))
                    .summary_grade("")
                    .ai_preview(new ArrayList<>())
                    .is_recommended(false)
                    .build();
            pathList.add(path);
        }
        return pathList;
    }
}
