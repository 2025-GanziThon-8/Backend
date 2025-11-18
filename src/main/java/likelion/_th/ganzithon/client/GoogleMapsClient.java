//package likelion._th.ganzithon.client;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import likelion._th.ganzithon.domain.LatLng;
//import likelion._th.ganzithon.dto.request.PathSearchRequest;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//import org.springframework.web.reactive.function.client.WebClient;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//
//@Component
////@RequiredArgsConstructor
//@Slf4j
//public class GoogleMapsClient {
//
//    private final WebClient webClient;
//    private String googleMapApiKey;
//
//    // 명시적 생성자
//    public GoogleMapsClient(
//            @Qualifier("googleMapClient") WebClient webClient,
//            @Value("${external-api.google.api-key}") String googleMapApiKey
//    ) {
//        this.webClient = webClient;
//        this.googleMapApiKey = googleMapApiKey;
//    }
//
//    public List<GoogleRoute> getGoogleMapPaths(PathSearchRequest request) {
//        String origin = request.getStartLat() + "," + request.getStartLng();
//        String destination = request.getEndLat() + "," + request.getEndLng();
//
//        // google direction api 호출
//        var uriBuilder = webClient.get()
//                .uri(uriBuilder1 -> {
//                    var builder = uriBuilder1
//                            .path("/maps/api/directions/json")
//                            .queryParam("origin", origin)
//                            .queryParam("destination", destination)
//                            .queryParam("model", "walking") // 도보
//                            .queryParam("alternatives", "true") // 여러 경로 받기
//                            .queryParam("key",googleMapApiKey);
//
//                    // 경유지 있을 경우 queryParam에 경유지 위도 경도 추가
//                    if (request.hasWaypoint()) {
//                        builder.queryParam("waypoints", request.getWaypointLat() + "," + request.getWaypointLng());
//                    }
//                    return builder.build();
//                });
//
//        log.info("[GoogleMapsClient] 요청 URI: {}", uriBuilder.toString());
//
//        JsonNode response;
//        try {
//            response = uriBuilder
//                    .retrieve()
//                    .bodyToMono(JsonNode.class)
//                    .block();
//        } catch (Exception e) {
//            log.error("[GoogleMapsClient] API 호출 실패", e);
//            throw new RuntimeException("Google Maps API 호출 실패", e);
//        }
//
//        if (response == null) {
//            log.error("[GoogleMapsClient] Google Maps 응답이 null입니다.");
//            return Collections.emptyList();
//        }
//
//        if (!response.has("routes")) {
//            log.error("[GoogleMapsClient] 응답에 routes 필드가 없습니다. 응답 내용: {}", response.toString());
//            return Collections.emptyList();
//        }
//
//        log.info("[GoogleMapsClient] Google Maps 응답 수신, routes 개수: {}", response.get("routes").size());
//
//        return parseGoogleRoutes(response);
//    }
//
//    // 구글 응답 파싱
//    private List<GoogleRoute> parseGoogleRoutes(JsonNode response) {
//        List<GoogleRoute> routes = new ArrayList<>();
//
//        JsonNode routesNode = response.get("routes");
//        if (routesNode == null || !routesNode.isArray()) {
//            return routes;
//        }
//
//        for (JsonNode routeNode : routesNode) {
//            try {
//                // 거리와 시간 추출
//                JsonNode leg = routeNode.get("legs").get(0);
//                int distance = leg.get("distance").get("value").asInt(); // 미터
//                int duration = leg.get("duration").get("value").asInt(); // 초
//
//                // Polyline에서 좌표 추출
//                String encodedPolyline = routeNode.get("overview_polyline").get("points").asText();
//
//                // 디코딩된 좌표
//                List<LatLng> coordinates = decodePolyline(encodedPolyline);
//
//                routes.add(GoogleRoute.builder()
//                        .distance(distance) // 거리
//                        .duration(duration) // 시간
//                        .coordinates(coordinates) // 원본 인코딩 문자열
//                        .build());
//
//            } catch (Exception e) {}
//        }
//        return routes;
//    }
//
//    // 디코딩
//    private List<LatLng> decodePolyline(String encoded) {
//        List<LatLng> poly = new ArrayList<>();
//        int index = 0, len = encoded.length();
//        int lat = 0, lng = 0;
//
//        while (index < len) {
//            int b, shift = 0, result = 0;
//            do {
//                b = encoded.charAt(index++) - 63;
//                result |= (b & 0x1f) << shift;
//                shift += 5;
//            } while (b >= 0x20);
//            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
//            lat += dlat;
//
//            shift = 0;
//            result = 0;
//            do {
//                b = encoded.charAt(index++) - 63;
//                result |= (b & 0x1f) << shift;
//                shift += 5;
//            } while (b >= 0x20);
//            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
//            lng += dlng;
//
//            poly.add(new LatLng(lat / 1e5, lng / 1e5));
//        }
//
//        return poly;
//    }
//
//    @lombok.Getter
//    @lombok.Builder
//    public static class GoogleRoute {
//        private Integer distance;  // 미터
//        private Integer duration;  // 초
//        private List<LatLng> coordinates; // 디코딩된 좌표
//        private String encodePolyline; // 인코딩된 문자열
//    }
//}
