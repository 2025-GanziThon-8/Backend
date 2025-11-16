package likelion._th.ganzithon.dto.request;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportRequest {
    private String routeId; // 선택한 경로 id ex) path-1
    private String origin; // 출발지 이름
    private String destination; // 도착지 이름
    private Integer totalDistance; // 총 거리
    private Integer totalTime; // 총 시간
    private List<Coordinate> coordinates; // 경로 좌표 목록

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coordinate {
        private Double lat;
        private Double lng;
    }
}
