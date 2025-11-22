package likelion._th.ganzithon.client;

import com.fasterxml.jackson.databind.JsonNode;
import likelion._th.ganzithon.domain.LatLng;
import likelion._th.ganzithon.dto.request.ReportRequest;
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
     * 임의 경유지 생성
     * - offset: 100m
     * - 500m 이하 ? 중간지점 경유 : 1/3 2/3 지점 경유
     * 경유지 O -> 해당 경유지 + 북/남쪽 경유지 추가
     * 경유지 X -> 북/남쪽 경유지 추가
     * 총 3개 반환
     */
    public List<TmapRoute> getRoutes(Double startLat, Double startLng,
                                     Double endLat, Double endLng,
                                     Double waypointLat, Double waypointLng) {

        List<TmapRoute> routes = new ArrayList<>();
        int totalDistance = 0;

        // 1. 기본 경로 (경유지 없음)
        TmapRoute route1 = getRoute(startLat, startLng, endLat, endLng, null);
        if (route1 != null) {
            routes.add(route1);
            totalDistance = route1.getDistance(); // fractions 지정 위해 총 거리 가져옴
        }

        // 우회 경로 생성을 위해 분할 지점 목록 생성
        List<Double> fractions = new ArrayList<>();
        if (totalDistance > 0 && totalDistance <= 500) {
            // 500m 이하: 중간 지점 1개만 사용 (1/2 지점)
            fractions.add(0.5);
        } else if (totalDistance > 500) {
            // 500m 초과: 2개 경유지 사용 (1/3, 2/3 지점). 1000m 초과 여부는 계산에 영향을 주지 않음.
            fractions.add(1.0 / 3.0);
            fractions.add(2.0 / 3.0);
        } else {
            // 거리가 0이거나 경로를 찾지 못한 경우
            return routes;
        }

        // 2. 사용자가 경유지 제공 시 해당 경로 추가
        if (waypointLat != null && waypointLng != null &&
                waypointLat != 0.0 && waypointLng != 0.0) {
            List<LatLng> userWaypoints = Collections.singletonList(new LatLng(waypointLat, waypointLng));
            TmapRoute userRoute = getRoute(startLat, startLng, endLat, endLng, userWaypoints);
            if (userRoute != null) {
                routes.add(userRoute); // routes.size()는 최대 2
            }
        }

        // 3. 경유지가 없거나 3개 경로 미만일 경우, 북쪽/남쪽 우회 경로 추가
        if (routes.size() < 3) {
            log.info("   🧭 북쪽 우회 경로 생성...");
            List<LatLng> northWaypoints = new ArrayList<>();
            for (double fraction : fractions) {
                LatLng waypoint = calculateWaypoint(
                        startLat, startLng, endLat, endLng, "north", fraction
                );
                northWaypoints.add(waypoint);
                log.debug("      북쪽 경유지 {}: ({},{})",
                        fraction, waypoint.getLat(), waypoint.getLng());
            }
            TmapRoute northRoute = getRoute(startLat, startLng, endLat, endLng, northWaypoints);

            if (northRoute != null && routes.size() < 3) {
                routes.add(northRoute);
                log.info("   ✓ 북쪽 우회: {}m, {}초",
                        northRoute.getDistance(), northRoute.getDuration());
            }
        }

        if (routes.size() < 3) {
            log.info("   🧭 남쪽 우회 경로 생성...");
            List<LatLng> southWaypoints = new ArrayList<>();
            for (double fraction : fractions) {
                LatLng waypoint = calculateWaypoint(
                        startLat, startLng, endLat, endLng, "south", fraction
                );
                southWaypoints.add(waypoint);
                log.debug("      남쪽 경유지 {}: ({},{})",
                        fraction, waypoint.getLat(), waypoint.getLng());
            }
            TmapRoute southRoute = getRoute(startLat, startLng, endLat, endLng, southWaypoints);

            if (southRoute != null && routes.size() < 3) {
                routes.add(southRoute);
                log.info("   ✓ 남쪽 우회: {}m, {}초",
                        southRoute.getDistance(), southRoute.getDuration());
            }
        }

        // 5. 최종적으로 3개를 초과하지 않도록 보장
        while (routes.size() > 3) {
            routes.remove(routes.size() - 1);
        }

        return routes;
    }

    /**
     * Tmap API 단일 경로 조회
     */
    private TmapRoute getRoute(Double startLat, Double startLng,
                               Double endLat, Double endLng,
                               List<LatLng> waypoints) {

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("startX", String.valueOf(startLng));
        formData.add("startY", String.valueOf(startLat));
        formData.add("endX", String.valueOf(endLng));
        formData.add("endY", String.valueOf(endLat));
        formData.add("reqCoordType", "WGS84GEO");
        formData.add("resCoordType", "WGS84GEO");
        formData.add("startName", "출발");
        formData.add("endName", "도착");
        formData.add("searchOption", "0"); // 0: 기본, 4: 추천+대로우선, 10: 최단, 30: 최단거리+계단제외

        if (waypoints != null && !waypoints.isEmpty()) {
            StringBuilder passListBuilder = new StringBuilder();
            for(int i = 0; i < waypoints.size(); i++) {
                LatLng wp = waypoints.get(i);

                passListBuilder.append(String.format("%.8f,%.8f", wp.getLng(), wp.getLat()));
                if (i < waypoints.size() - 1) {
                    passListBuilder.append("_");
                }
            }
            formData.add("passList", passListBuilder.toString());
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
        List<ReportRequest.Coordinate> coordinates = new ArrayList<>();
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
                            coordinates.add(new ReportRequest.Coordinate(lat, lng));
                        }
                    }
                }
            }
        }

        return TmapRoute.builder()
                .distance(totalDistance)
                .duration(totalTime)
                .coordinates(coordinates)
                .encodedPolyline(coordinates)
                .build();
    }

    // 좌표 인코딩 (간단한 JSON 형식)
    private String encodeCoordinates(List<ReportRequest.Coordinate> coordinates) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < coordinates.size(); i++) {
            ReportRequest.Coordinate coord = coordinates.get(i);
            sb.append(String.format("{\"lat\":%.6f,\"lng\":%.6f}", coord.getLat(), coord.getLng()));
            if (i < coordinates.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    // 경유지 계산 (출발지-도착지 중간에서 북쪽/남쪽으로 이동)
    private LatLng calculateWaypoint(double startLat, double startLng,
                                     double endLat, double endLng,
                                     String direction, double fraction) {
        // 중간 지점
        double midLat = startLat + (endLat - startLat) * fraction;
        double midLng = startLng + (endLng - startLng) * fraction;

        // 300m ≈ 0.003도
        double offset = 0.001;

        if (direction.equals("north")) {
            return new LatLng(midLat + offset, midLng);
        } else {
            return new LatLng(midLat - offset, midLng);
        }
    }

    // Tmap 경로 데이터 모델
    @lombok.Getter
    @lombok.Builder
    public static class TmapRoute {
        private Integer distance;  // 미터
        private Integer duration;  // 초
        private List<ReportRequest.Coordinate> coordinates;
        private List<ReportRequest.Coordinate> encodedPolyline;
    }
}