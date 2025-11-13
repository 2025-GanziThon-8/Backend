package likelion._th.ganzithon.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
// 경로 조회 요청
public class PathSearchRequest {
    @NotNull
    @JsonProperty("start_lat")
    private Double startLat; // 출발지 위도

    @NotNull
    @JsonProperty("start_lng")
    private Double startLng; // 출발지 경도

    @NotNull
    @JsonProperty("end_lat")
    private Double endLat; // 도착지 위도

    @NotNull
    @JsonProperty("end_lng")
    private Double endLng; //도착지 경도

    @NotNull
    @JsonProperty("start_name")
    private String startName;

    @NotNull
    @JsonProperty("end_name")
    private String endName;
}
