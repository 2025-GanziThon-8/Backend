package likelion._th.ganzithon.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportRequest {
    private String routeId;
    private String origin;
    private String destination;
    private Integer totalDistance;
    private Integer totalTime;
    private List<Coordinate> coordinates;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coordinate {
        private Double lat;
        private Double lng;
    }
}
