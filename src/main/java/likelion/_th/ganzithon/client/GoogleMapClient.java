package likelion._th.ganzithon.client;

import likelion._th.ganzithon.dto.request.PathSearchRequest;
import likelion._th.ganzithon.dto.response.PathInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GoogleMapClient {

    private final WebClient googleMapClient;

    @Value("${external-api.google-map.api-key}")
    private String googleMapApiKey;

    public List<PathInfo> getGoogleMapPaths(PathSearchRequest request) {
        String origin = request.getStartLat() + "," + request.getStartLng();
        String destination = request.getEndLat() + "," + request.getEndLng();

        // google direction api 호출
        Map<String, Object> response = googleMapClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("origin", origin) // 출발지
                        .queryParam("destination", destination) // 도착지
                        .queryParam("mode", "walking") // 도보
                        .queryParam("alternatives", "true") // 여러 경로 받음
                        .queryParam("key", googleMapApiKey)
                        .build()
                )
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        // 호출한 데이터 받음
        List<Map<String, Object>> routes = (List<Map<String, Object>>) response.get("routes");
        // 호출 받은 데이터 넣을 배열
        List<PathInfo> pathList = new ArrayList<>();
        int idx = 1;

        for (Map<String, Object> route : routes) {
            if (idx > 3) break; // 최대 3개 경로

            Map<String, Object> leg = ((List<Map<String, Object>>) route.get("legs")).get(0);

            int duration = ((Number) ((Map<String, Object>) leg.get("duration")).get("value")).intValue(); // 초
            int distance = ((Number) ((Map<String, Object>) leg.get("distance")).get("value")).intValue(); // 미터

            String polyline = ((Map<String, Object>) route.get("overview_polyline")).get("points").toString();

            PathInfo path = PathInfo.builder()
                    .id("path-" + idx++)
                    .time(duration)
                    .distance(distance)
                    .polyline(polyline)
                    .cpted(Map.of("avg", 0.0)) // 초기값, 후에 리포트 생성시 업데이트
                    .summary_grade("")
                    .ai_preview(new ArrayList<>())
                    .is_recommended(false)
                    .build();

            pathList.add(path);
        }

        return pathList;

    }
}
