package likelion._th.ganzithon.client;

import com.fasterxml.jackson.databind.JsonNode;
import likelion._th.ganzithon.domain.LatLng;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Component
@Slf4j
public class TmapsClient {

    private final WebClient webClient;
    private final String tmapApiKey;

    public TmapsClient(
            @Qualifier("tmapClient") WebClient webClient,
            @Value("${external-api.tmap.api-key}") String tmapApiKey
    ) {
        this.webClient = webClient;
        this.tmapApiKey = tmapApiKey;
    }

    /**
     * Tmap 도보 경로 조회 (여러 경로 생성)
     */
    public List<TmapRoute> getRoutes(Double startLat, Double startLng,
                                     Double endLat, Double endLng,
                                     Double waypointLat, Double waypointLng) {

        List<TmapRoute> routes = new ArrayList<>();

        // 1. 기본 경로 (경유지 없음)
        TmapRoute route1 = getRoute(startLat, startLng, endLat, endLng, null, null);
        if (route1 != null) routes.add(route1);

        // 2. 경유지 제공 시 해당 경로 추가
        if (waypointLat != null && waypointLng != null) {
            TmapRoute route2 = getRoute(startLat, startLng, endLat, endLng, waypointLat, waypointLng);
            if (route2 != null) routes.add(route2);
        }

        // 3. 경유지가 없거나 3개 경로 미만일 경우, 북쪽/남쪽 우회 경로 추가
        if (routes.size() < 3) {
            LatLng northWaypoint = calculateWaypoint(startLat, startLng, endLat, endLng, "north");
            TmapRoute route2 = getRoute(startLat, startLng, endLat, endLng, northWaypoint.getLat(), northWaypoint.getLng());
            if (route2 != null) routes.add(route2);
        }

        if (routes.size() < 3) {
            LatLng southWaypoint = calculateWaypoint(startLat, startLng, endLat, endLng, "south");
            TmapRoute route3 = getRoute(startLat, startLng, endLat, endLng, southWaypoint.getLat(), southWaypoint.getLng());
            if (route3 != null) routes.add(route3);
        }

        return routes;
    }

    /**
     * Tmap API 단일 경로 조회
     */
    private TmapRoute getRoute(Double startLat, Double startLng,
                               Double endLat, Double endLng,
                               Double waypointLat, Double waypointLng) {

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("startX", String.valueOf(startLng));
        formData.add("startY", String.valueOf(startLat));
        formData.add("endX", String.valueOf(endLng));
        formData.add("endY", String.valueOf(endLat));
        formData.add("reqCoordType", "WGS84GEO");
        formData.add("resCoordType", "WGS84GEO");
        formData.add("startName", "출발");
        formData.add("endName", "도착");
        if (waypointLat != null && waypointLng != null) {
            formData.add("passList", String.format("%.8f,%.8f", waypointLng, waypointLat));
        }

        JsonNode response = webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/tmap/routes/pedestrian")
                        .queryParam("version", "1")
                        .queryParam("format", "json")
                        .build())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            return parseTmapRoute(response);

//        } catch (WebClientResponseException e) {
//            log.error("Tmap API 오류: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
//            throw new ExternalApiException("Tmap", e.getRawStatusCode(), "Tmap API 호출에 실패했습니다.");
//        } catch (Exception e) {
//            log.error("Tmap API 오류", e);
//            throw new ExternalApiException("Tmap", "경로 조회 중 오류가 발생했습니다.", e);
//        }
    }

    /**
     * Tmap 응답 파싱
     */
    private TmapRoute parseTmapRoute(JsonNode response) {
        if (response == null || !response.has("features")) {
            return null;
        }

        JsonNode features = response.get("features");

        // 총 거리와 시간 추출
        int totalDistance = 0;
        int totalTime = 0;
        List<LatLng> coordinates = new ArrayList<>();
        StringBuilder polylineBuilder = new StringBuilder();

        for (JsonNode feature : features) {
            JsonNode properties = feature.get("properties");

            if (properties.has("totalDistance")) {
                totalDistance = properties.get("totalDistance").asInt();
            }
            if (properties.has("totalTime")) {
                totalTime = properties.get("totalTime").asInt();
            }

            // 좌표 추출
            JsonNode geometry = feature.get("geometry");
            if (geometry != null && geometry.has("coordinates")) {
                JsonNode coords = geometry.get("coordinates");

                // LineString인 경우
                if (coords.isArray()) {
                    for (JsonNode coord : coords) {
                        if (coord.isArray() && coord.size() >= 2) {
                            double lng = coord.get(0).asDouble();
                            double lat = coord.get(1).asDouble();
                            coordinates.add(new LatLng(lat, lng));
                        }
                    }
                }
            }
        }

        // 좌표를 Polyline으로 인코딩 (간단하게 JSON 문자열로)
        String encodedPolyline = encodeCoordinates(coordinates);

        return TmapRoute.builder()
                .distance(totalDistance)
                .duration(totalTime)
                .coordinates(coordinates)
                .encodedPolyline(encodedPolyline)
                .build();
    }

    /**
     * 좌표 인코딩 (간단한 JSON 형식)
     */
    private String encodeCoordinates(List<LatLng> coordinates) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < coordinates.size(); i++) {
            LatLng coord = coordinates.get(i);
            sb.append(String.format("{\"lat\":%.6f,\"lng\":%.6f}", coord.getLat(), coord.getLng()));
            if (i < coordinates.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 경유지 계산 (출발지-도착지 중간에서 북쪽/남쪽으로 이동)
     */
    private LatLng calculateWaypoint(double startLat, double startLng,
                                     double endLat, double endLng,
                                     String direction) {
        // 중간 지점
        double midLat = (startLat + endLat) / 2;
        double midLng = (startLng + endLng) / 2;

        // 300m ≈ 0.003도
        double offset = 0.003;

        if (direction.equals("north")) {
            return new LatLng(midLat + offset, midLng);
        } else {
            return new LatLng(midLat - offset, midLng);
        }
    }

    /**
     * Tmap 경로 데이터 모델
     */
    @lombok.Getter
    @lombok.Builder
    public static class TmapRoute {
        private Integer distance;  // 미터
        private Integer duration;  // 초
        private List<LatLng> coordinates;
        private String encodedPolyline;
    }
}