package likelion._th.ganzithon.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import likelion._th.ganzithon.client.UpstageAiClient;
import likelion._th.ganzithon.domain.LatLng;
import likelion._th.ganzithon.dto.RouteAnalysisData;
import likelion._th.ganzithon.dto.request.ReportRequest;
import likelion._th.ganzithon.dto.response.ReportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
// ai 리포트 생성 서비스
public class ReportService {
    private final CptedService cptedService;
    private final UpstageAiClient upstageAiClient;

    public ReportResponse generateReport(ReportRequest request) throws JsonProcessingException, ExecutionException, InterruptedException, TimeoutException {
        // 1. 좌표 반환
        List<LatLng> coordinates = request.getCoordinates().stream()
                .map(c -> new LatLng(c.getLat(), c.getLng()))
                .collect(Collectors.toList());

        // 2. CPTED 전체 분석 (구간별 포함)
        RouteAnalysisData analysis = cptedService.analyzeRoute(
                request.getRouteId(),
                coordinates,
                request.getTotalDistance(),
                request.getTotalTime()
        );

        // 3. CPTED 5대 평가 항목 계산
        ReportResponse.CptedEvaluation cptedEval = calculateCptedEvaluation(analysis);

        // 4. 구간별 안내 생성
        List<ReportResponse.SegmentGuide> segmentGuides = buildSegmentGuides(analysis.getSegments());

        // 5. AI 종합 코멘트 생성
        String aiSummary = upstageAiClient.generateDetailReport(
                request.getOrigin(),
                request.getDestination(),
                analysis,
                cptedEval
        );

        // 6. 경로 요약 정보
        String overallGrade = calculateOverallGrade(analysis.getCptedAvg());
        ReportResponse.RouteSummary routeSummary = ReportResponse.RouteSummary.builder()
                .origin(request.getOrigin())
                .destination(request.getDestination())
                .totalDistance(request.getTotalDistance())
                .totalTime(request.getTotalTime())
                .overallGrade(overallGrade)
                .build();

        // 7. 최종 리포트 생성
        ReportResponse.ReportDetail reportDetail = ReportResponse.ReportDetail.builder()
                .routeSummary(routeSummary)
                .cptedEvaluation(cptedEval)
                .segmentGuides(segmentGuides)
                .aiSummary(aiSummary)
                .createdAt(LocalDateTime.now())
                .build();

        return ReportResponse.builder()
                .message("CPTED 리포트 생성 성공")
                .report(reportDetail)
                .build();
    }

    // CPTED 5대 평가 항목 계산
    private ReportResponse.CptedEvaluation calculateCptedEvaluation(
            RouteAnalysisData analysis
    ){
        int cctv = analysis.getCctvCount();
        int light = analysis.getLightCount();
        int store = analysis.getStoreCount();
        int police = analysis.getPoliceCount();

        // 1. 자연감시
        int naturalSurveillance = calculateScore(cctv * 4 + light * 3, 70);

        // 2. 접근 통제
        int accessControl = calculateScore(police * 10 + (cctv + light + store), 40);

        //3. 영역성 강화
        int territoriality = calculateScore(store * 8 + light * 2, 50);

        // 4. 활동성 증대
        int activitySupport = calculateScore(store * 10, 50);

        // 5. 유지관리
        int maintenance = calculateScore(light * 4, 60);

        return ReportResponse.CptedEvaluation.builder()
                .naturalSurveillance(buildCptedItem("자연감시", naturalSurveillance,
                        generateDescription("자연감시", naturalSurveillance, cctv, light)))
                .accessControl(buildCptedItem("접근통제", accessControl,
                        generateDescription("접근통제", accessControl, cctv, light)))
                .territoriality(buildCptedItem("영역성 강화", territoriality,
                        generateDescription("영역성", territoriality, store, 0)))
                .activitySupport(buildCptedItem("활동성 증대", activitySupport,
                        generateDescription("활동성", activitySupport, store, 0)))
                .maintenance(buildCptedItem("유지관리", maintenance,
                        generateDescription("유지관리", maintenance, light, 0)))
                .facilities(ReportResponse.Facilities.builder()
                        .cctvCount(cctv)
                        .lightCount(light)
                        .storeCount(store)
                        .policeCount(police)
                        .schoolCount(0)
                        .build())
                .build();
    }

    // 점수 계산 (0-100 범위로)
    private int calculateScore(int rawValue, int baseline) {
        return Math.min(100, baseline + rawValue);
    }

    // CPTED 항목 생성
    private ReportResponse.CptedItem buildCptedItem(String name, int score, String description) {
        return ReportResponse.CptedItem.builder()
                .name(name)
                .score(score)
                .description(description)
                .build();
    }

    // CPTED 항목별, 점수별 설명 생성
    private String generateDescription(String category, int score, int facility1, int facility2) {
        switch (category) {
            case "자연감시":
                if (score >= 85) return String.format("밝고 CCTV %d개 다수 존재", facility1);
                if (score >= 70) return "CCTV와 조명이 적절히 배치됨";
                return "CCTV와 조명이 부족함";

            case "접근통제":
                if (score >= 80) return "통제된 출입구와 울타리 존재";
                if (score >= 60) return "개방형 골목 다수 존재";
                return "통제되지 않은 골목 다수";

            case "영역성":
                if (score >= 80) return "상가/주택 혼합";
                if (score >= 60) return "주거 지역";
                return "영역성 불분명";

            case "활동성":
                if (score >= 80) return "주간/야간 활발한 유동인구";
                if (score >= 60) return "야간 인적 드묾";
                return "전반적으로 인적 드묾";

            case "유지관리":
                if (score >= 85) return "조명/청결 양호";
                if (score >= 70) return "조명/청결 보통";
                return "조명/청결 관리 필요";

            default:
                return "";
        }
    }

    // 구간별 안내 사항
    private List<ReportResponse.SegmentGuide> buildSegmentGuides(
            List<RouteAnalysisData.SegmentAnalysis> segments) {

        List<ReportResponse.SegmentGuide> guides = new ArrayList<>();

        for (RouteAnalysisData.SegmentAnalysis segment : segments) {
            String distanceRange = String.format("%d~%dm", segment.getStartDistance(), segment.getEndDistance());
            List<String> recommendations = getRecommendations(segment.getSafetyLevel(), segment.getDescription());

            guides.add(ReportResponse.SegmentGuide.builder()
                    .distanceRange(distanceRange)
                    .description(segment.getDescription())
                    .safetyLevel(segment.getSafetyLevel())
                    .recommendations(recommendations)
                    .build());
        }

        return guides;
    }

    // 구간별 추천 사항
    private List<String> getRecommendations(String safetyLevel, String description) {
        List<String> recommendations = new ArrayList<>();

        switch (safetyLevel) {
            case "안전":
                recommendations.add("안전한 구간입니다");
                break;
            case "주의":
                recommendations.add("주변을 살피며 이동하세요");
                if (description.contains("어두움") || description.contains("골목")) {
                    recommendations.add("빠르게 통과하세요");
                }
                break;
            case "위험":
                recommendations.add("매우 조심하세요");
                recommendations.add("가능하면 다른 경로 이용");
                break;
        }

        return recommendations;
    }

    // 종합 등급 계산
    private String calculateOverallGrade(double cptedAvg) {
        String grade;
        if (cptedAvg >= 4.0) grade = "S";
        else if (cptedAvg >= 3.0) grade = "A";
        else if (cptedAvg >= 2.0) grade = "B";
        else if (cptedAvg >= 1.0) grade = "C";
        else grade = "D";

        int scaledScore = (int) Math.min(cptedAvg * 20, 100);
        return String.format("%s (%d점)", grade, scaledScore);
    }

    // 등급별 텍스트
    private String getGradeText(int score) {
        if (score >= 90) return "매우 우수";
        if (score >= 80) return "우수";
        if (score >= 70) return "양호";
        if (score >= 60) return "보통";
        return "주의 필요";
    }

}
