package likelion._th.ganzithon.client;

import com.fasterxml.jackson.databind.JsonNode;
import likelion._th.ganzithon.domain.LatLng;
import likelion._th.ganzithon.dto.request.PathSearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class GoogleMapClient {

    @Qualifier("googleMapClient")
    private final WebClient webClient;

    @Value("${external-api.google-map.api-key}")
    private String googleMapApiKey;

    public List<GoogleRoute> getGoogleMapPaths(PathSearchRequest request) {
        String origin = request.getStartLat() + "," + request.getStartLng();
        String destination = request.getEndLat() + "," + request.getEndLng();

        // google direction api 호출
        JsonNode response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/maps/api/directions/json")
                        .queryParam("origin", origin)
                        .queryParam("destination", destination)
                        .queryParam("model", "walking")
                        .queryParam("alternatives", "true")
                        .queryParam("key",googleMapApiKey)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        return parseGoogleRoutes(response);
    }

    // 구글 응답 파싱
    private List<GoogleRoute> parseGoogleRoutes(JsonNode response) {
        List<GoogleRoute> routes = new ArrayList<>();

        JsonNode routesNode = response.get("routes");
        if (routesNode == null || !routesNode.isArray()) {
            return routes;
        }

        for (JsonNode routeNode : routesNode) {
            try {
                // 거리와 시간 추출
                JsonNode leg = routeNode.get("legs").get(0);
                int distance = leg.get("distance").get("value").asInt(); // 미터
                int duration = leg.get("duration").get("value").asInt(); // 초

                // Polyline에서 좌표 추출
                String encodedPolyline = routeNode.get("overview_polyline").get("points").asText();

                // 디코딩된 좌표
                List<LatLng> coordinates = decodePolyline(encodedPolyline);

                routes.add(GoogleRoute.builder()
                        .distance(distance) // 거리
                        .duration(duration) // 시간
                        .coordinates(coordinates) // 원본 인코딩 문자열
                        .build());

            } catch (Exception e) {}
        }
        return routes;
    }

    // 디코딩
    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            poly.add(new LatLng(lat / 1e5, lng / 1e5));
        }

        return poly;
    }

    @lombok.Getter
    @lombok.Builder
    public static class GoogleRoute {
        private Integer distance;  // 미터
        private Integer duration;  // 초
        private List<LatLng> coordinates; // 디코딩된 좌표
        private String encodePolyline; // 인코딩된 문자열
    }
}
