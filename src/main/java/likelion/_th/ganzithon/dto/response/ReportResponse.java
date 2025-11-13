package likelion._th.ganzithon.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
// Ai 리포트 응답
public class ReportResponse {
    private String message;
    private Resport report;

    @Getter
    @Builder
    public static class Resport {
        private double cpted_score;
        private double total_distance; // 단위: m
        private double total_time; // 단위: 초
        private int cctv_count;
        private int light_count;
        private int store_count;
        private int police_count;
        private int school_count;
        private String ai_comment;
        private String created_at;
    }
}
