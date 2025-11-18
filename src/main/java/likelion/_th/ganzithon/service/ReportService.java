package likelion._th.ganzithon.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import likelion._th.ganzithon.client.UpstageAiClient;
import likelion._th.ganzithon.domain.LatLng;
import likelion._th.ganzithon.dto.RouteAnalysisData;
import likelion._th.ganzithon.dto.request.ReportRequest;
import likelion._th.ganzithon.dto.response.ReportResponse;
import likelion._th.ganzithon.util.PercentileCalculator;
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
    private PercentileCalculator percentileCalc;

    public ReportResponse generateReport(ReportRequest request) throws JsonProcessingException, ExecutionException, InterruptedException, TimeoutException {
        // 1. polyline 좌표 반환
        List<ReportRequest.Coordinate> coordinates = request.getCoordinates().stream()
                .map(c -> new ReportRequest.Coordinate(c.getLat(), c.getLng()))
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
        int school = analysis.getSchoolCount();

        // Percentile기반으로 점수만 계산
        int cctvPercentile = cptedService.getPercentileScore("cctv", cctv);
        int lightPercentile = cptedService.getPercentileScore("light", light);
        int storePercentile = cptedService.getPercentileScore("store", store);
        int policePercentile = cptedService.getPercentileScore("police", police);

        // 1. 자연감시: cctv 60% + 가로등 40%
        int naturalScore = (int)(cctvPercentile * 0.6 + lightPercentile * 0.4);
        // 2. 접근 통제: 경찰서
        int accessScore = policePercentile;
        //3. 영역성 강화: 학교
        int territorialityScore = getSchoolScore(school);
        // 4. 활동성 증대: 편의점
        int activityScore = storePercentile;
        // 5. 유지관리: 가로등
        int maintenanceScore = lightPercentile;

        return ReportResponse.CptedEvaluation.builder()
                // 자연감시 - cctv, 가로등
                .naturalSurveillance(buildCptedItem(
                        "자연감시",
                        naturalScore,
                        generateDescription("자연감시", naturalScore, cctv, light)))
                // 접근 통제 - 경찰서
                .accessControl(buildCptedItem(
                        "접근통제",
                        accessScore,
                        generateDescription("접근통제", accessScore, police, 0)))
                .territoriality(buildCptedItem(
                        "영역성 강화",
                        territorialityScore,
                        generateDescription("영역성", territorialityScore, school, 0)))
                .activitySupport(buildCptedItem(
                        "활동성 증대",
                        activityScore,
                        generateDescription("활동성", activityScore, store, 0)))
                .maintenance(buildCptedItem(
                        "유지관리",
                        maintenanceScore,
                        generateDescription("유지관리", maintenanceScore, light, 0)))
                .facilities(ReportResponse.Facilities.builder()
                        .cctvCount(cctv)
                        .lightCount(light)
                        .storeCount(store)
                        .policeCount(police)
                        .schoolCount(school)
                        .build())
                .build();
    }

    // 학교는 절대값 기준 (개수가 적어서)
    private int getSchoolScore(int schoolCount) {
        if (schoolCount >= 3) return 95;
        if (schoolCount == 2) return 85;
        if (schoolCount == 1) return 70;
        return 40;
    }

    // CPTED 항목 생성
    private ReportResponse.CptedItem buildCptedItem(String name, int score, String description) {
        return ReportResponse.CptedItem.builder()
                .name(name)
                .score(score)
                .description(description)
                .build();
    }

    // <CPTED 평가> 항목별, 점수별 설명 생성
    private String generateDescription(
            String category, int score, int facility1, int facility2) {

        switch (category) {
            case "자연감시":
                if (score >= 85) {
                    return "밝고 CCTV 다수 존재";
                } else if (score >= 70) {
                    return "CCTV와 조명이 적절히 배치됨";
                } else if (score >= 50) {
                    return "CCTV 또는 조명 보완 필요";
                } else {
                    return "CCTV와 조명 부족";
                }

            case "접근통제":
                if (score >= 80) {
                    return "통제된 출입구와 감시 체계 존재";
                } else if (score >= 65) {
                    return "개방형 골목 다수 존재";
                } else if (score >= 45) {
                    return "통제되지 않은 골목 존재";
                } else {
                    return "접근 통제 미흡";
                }

            case "영역성":
                if (score >= 80) {
                    return "상가/주택 혼합";
                } else if (score >= 65) {
                    return "주거 중심 영역";
                } else if (score >= 45) {
                    return "영역성 보통";
                } else {
                    return "영역성 불분명";
                }

            case "활동성":
                if (score >= 80) {
                    return "유동인구 활발";
                } else if (score >= 65) {
                    return "일부 시간대 인적 드묾";
                } else if (score >= 45) {
                    return "야간 인적 드묾";
                } else {
                    return "전반적으로 인적 드묾";
                }

            case "유지관리":
                if (score >= 85) {
                    return "조명/청결 양호";
                } else if (score >= 70) {
                    return "조명/청결 보통";
                } else if (score >= 50) {
                    return "일부 구간 관리 필요";
                } else {
                    return "조명/청결 관리 필요";
                }

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
        if (cptedAvg >= 4.0) grade = "A"; // 80~100점
        else if (cptedAvg >= 3.0) grade = "B"; // 60~79점
        else if (cptedAvg >= 2.0) grade = "C"; // 40~59점
        else if (cptedAvg >= 1.0) grade = "D"; // 20~39점
        else grade = "F"; // 0~19점

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
