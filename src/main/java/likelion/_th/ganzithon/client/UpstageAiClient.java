package likelion._th.ganzithon.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import likelion._th.ganzithon.dto.RouteAnalysisData;
import likelion._th.ganzithon.dto.response.ReportResponse;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
//@RequiredArgsConstructor
@Slf4j
// upstage ai 호출
public class UpstageAiClient {

//    @Qualifier("upstageClient")
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UpstageAiClient(@Qualifier("upstageClient") WebClient webClient) {
        this.webClient = webClient;
    }

    // AI가 3개 경로 중 추천 경로 선택
    public String selectRecommendedRoute(List<RouteAnalysisData> routes) {
        if(routes== null || routes.size() < 3) {
            log.warn("경로가 3개 미만입니다. 첫 번째 경로를 추천합니다.");
            // 경로가 없거나, 3개 미만 -> 첫번째 경로 반환
            return routes.get(0).getRouteId();
        }

        try {
            String prompt = buildRouteComparisonPrompt(routes); // 프롬프트
            String response = generateText(prompt);

            return parseRecommendedRouteId(response, routes);
        } catch (Exception e) {
            log.error("AI 경로 선택 오류", e);
            return routes.stream()
                    .max((a,b) -> Double.compare(a.getCptedAvg(), b.getCptedAvg()))
                    .map(RouteAnalysisData::getRouteId)
                    .orElse(routes.get(0).getRouteId());
        }
    }
    
    // /paths: 경로별 AI 프리뷰 생성 (1-3줄)
    public List<String> generateRoutePreview(RouteAnalysisData route) {
        try {
            String prompt = buildPreviewPrompt(route);
            String response = generateText(prompt);
            return parsePreviewLines(response);
        } catch (Exception e) {
            log.warn("AI 프리뷰 생성 실패: {}", e.getMessage());
            return generateDefaultPreview(route);
        }
    }

    // /reports: 상세 리포트용 AI 코멘트 생성 (3-4줄)
    public String generateDetailReport(
            String origin, String destination,
            RouteAnalysisData analysis, ReportResponse.CptedEvaluation cptedEval
    ) throws JsonProcessingException {
        String prompt = buildDetailedPrompt(origin, destination, analysis, cptedEval);
        return generateText(prompt);
    }

    // upstage ai 호출
    public String generateText(String prompt) throws JsonProcessingException {
        Map<String, Object> requestBody = Map.of(
                "model", "solar-pro2",
                "reasoning_effort", "medium",
                "messages", new Object[]{
                        Map.of("role", "system", "content",
                                "당신은 CPTED 기반 안전 경로 분석 전문가입니다."),
                        Map.of("role", "user", "content", prompt)
                }
        );

        String responseJson = webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode jsonNode = objectMapper.readTree(responseJson);
        return jsonNode.get("choices").get(0).get("message").get("content").asText();
    }

    // ------------------------ 프롬프트 생성 메서드들 ----------------------------

    // 추천 경로 프롬프트
    private String buildRouteComparisonPrompt(List<RouteAnalysisData> routes) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음 3개의 도보 경로 중 가장 추천할 경로를 선택해 주세요.\n");
        sb.append("각 경로의 안전성과 거리, 소요 시간을 함께 고려해 주세요.\n\n");

        for (int i = 0; i < routes.size(); i++) {
            RouteAnalysisData route = routes.get(i);
            sb.append(String.format("[경로%d]\n", i + 1));
            sb.append(String.format("- 거리: %dm (도보 약 %d분)\n",
                    route.getDistance(), route.getTime() / 60));
            sb.append(String.format("- CPTED 안전 점수: %.1f점 (5점 만점)\n",
                    route.getCptedAvg()));
            sb.append(String.format("- CCTV: %d개, 가로등: %d개\n",
                    route.getCctvCount(), route.getLightCount()));
            sb.append(String.format("- 주의/위험 구간: %d개\n\n",
                    route.getRiskSegmentCount()));
        }
        sb.append("아래 형식을 정확히 지켜서 답변해 주세요.\n");
        sb.append("추천 경로: 경로N\n");
        sb.append("이유:\n");
        sb.append("1. 첫 번째 이유\n");
        sb.append("2. 두 번째 이유\n");
        sb.append("3. 세 번째 이유\n");

        return sb.toString();
    }

    // 경로별 ai_priview용 프롬프트
    // 경로별 AI 프리뷰용 프롬프트 (리뉴얼 버전)
    private String buildPreviewPrompt(RouteAnalysisData route) {
        double avg = route.getCptedAvg();
        int risk = route.getRiskSegmentCount();
        int cctv = route.getCctvCount();
        int light = route.getLightCount();

        // 전반 안전 수준 (점수 + 위험구간 기준)
        String safetyLevel;
        if (avg >= 4.0 && risk <= 1) {
            safetyLevel = "전반적으로 안전한 편";
        } else if (avg >= 3.0) {
            safetyLevel = "대체로 무난한 편";
        } else {
            safetyLevel = "조금 더 주의가 필요한 편";
        }

        // 밝기/조도 (가로등 + CCTV 대략 합산)
        int brightnessScore = light + (int) Math.round(cctv * 0.5);
        String brightnessLevel;
        if (brightnessScore >= 10) {
            brightnessLevel = "야간에도 비교적 밝은 편";
        } else if (brightnessScore >= 5) {
            brightnessLevel = "조명은 보통 수준";
        } else {
            brightnessLevel = "야간에는 다소 어두운 구간이 있을 수 있는 편";
        }

        // 주변 시선/감시 느낌 (CCTV 기준, '없다'라는 말은 안 쓰도록)
        String watchLevel;
        if (cctv >= 6) {
            watchLevel = "주변 시선과 감시가 꽤 느껴지는 편";
        } else if (cctv >= 3) {
            watchLevel = "사람이 어느 정도 지나다니는 편";
        } else {
            watchLevel = "사람이 적고 시선이 다소 부족할 수 있는 편";
        }

        // 위험/주의 구간 느낌 (개수 → 말로만 전달)
        String riskHint;
        if (risk == 0) {
            riskHint = "특별히 위험한 구간은 거의 없는 편";
        } else if (risk <= 2) {
            riskHint = "몇 곳 정도는 조금 더 신경 쓰면 좋은 구간";
        } else {
            riskHint = "여러 구간에서 주변을 살피며 이동하면 좋은 경로";
        }

        return String.format(
                "도보 경로의 안전성과 분위기를 간단히 안내해 주세요.\n\n" +
                        "[경로 기본 정보]\n" +
                        "- 도보 거리: %dm (약 %d분)\n" +
                        "- 전반 안전 수준: %s\n" +
                        "- 야간 밝기: %s\n" +
                        "- 주변 감시/시선: %s\n" +
                        "- 위험/주의 구간: %s\n\n" +
                        "[작성 규칙]\n" +
                        "1. 총 3줄, 각 줄은 20~30자 이내로 작성합니다.\n" +
                        "2. 숫자(개수, 점수, 구간 수 등)는 문장에 직접 쓰지 않습니다.\n" +
                        "3. 첫 번째 줄: 길 전체의 분위기를 한 줄로 요약합니다.\n" +
                        "4. 두 번째 줄: 밝기·사람·시설 등 이 경로의 장점을 중심으로 씁니다.\n" +
                        "5. 세 번째 줄: 주의해야 할 점과 이용 팁을 중심으로 씁니다.\n" +
                        "6. '-에요', '-예요' 형태의 부드러운 존댓말을 사용합니다.\n" +
                        "7. 세 줄은 서로 다른 내용을 담고, 비슷한 표현을 반복하지 마세요.\n" +
                        "8. CCTV나 가로등이 부족한 경우에도 '하나도 없다'라는 표현 대신 " +
                        "'적은 편이에요', '많지 않아요'처럼 완화된 표현을 사용해 주세요.",
                route.getDistance(),
                route.getTime() / 60,
                safetyLevel,
                brightnessLevel,
                watchLevel,
                riskHint
        );
    }


    // 사용자가 선택한 경로 리포트용 프롬프트
    private String buildDetailedPrompt(
            String origin,
            String destination,
            RouteAnalysisData analysis,
            ReportResponse.CptedEvaluation cptedEval
    ) {
        return String.format(
                "통학/도보 경로의 안전성을 간단히 요약해 주세요.\n\n" +
                        "[경로 정보]\n" +
                        "- 출발지: %s\n" +
                        "- 도착지: %s\n" +
                        "- 거리: %dm, 도보 약 %d분\n\n" +
                        "[CPTED 5대 평가 (0~100점)]\n" +
                        "1. 자연감시: %d점 - %s\n" +
                        "2. 접근통제: %d점 - %s\n" +
                        "3. 영역성: %d점 - %s\n" +
                        "4. 활동성: %d점 - %s\n" +
                        "5. 유지관리: %d점 - %s\n\n" +
                        "[시설 정보]\n" +
                        "- CCTV: %d개, 가로등: %d개\n" +
                        "- 주의/위험 구간: %d개\n\n" +
                        "[작성 규칙]\n" +
                        "1. 3~4줄로 간단히 작성\n" +
                        "2. 전체적인 안전 수준과 강점, 주의할 점을 함께 언급\n" +
                        "3. '-에요', '-예요' 등 친근한 존댓말 사용\n",
                origin,
                destination,
                analysis.getDistance(),
                analysis.getTime() / 60,
                cptedEval.getNaturalSurveillance().getScore(),
                cptedEval.getNaturalSurveillance().getDescription(),
                cptedEval.getAccessControl().getScore(),
                cptedEval.getAccessControl().getDescription(),
                cptedEval.getTerritoriality().getScore(),
                cptedEval.getTerritoriality().getDescription(),
                cptedEval.getActivitySupport().getScore(),
                cptedEval.getActivitySupport().getDescription(),
                cptedEval.getMaintenance().getScore(),
                cptedEval.getMaintenance().getDescription(),
                analysis.getCctvCount(),
                analysis.getLightCount(),
                analysis.getRiskSegmentCount()
        );
    }


    // 파싱 및 폴백 메서드들

    // 1,2,3번 경로중 어떤 경로인지 파싱해서 해당 경로 id 넘겨줌
    private String parseRecommendedRouteId(String aiResponse, List<RouteAnalysisData> routes) {
        if (aiResponse.contains("경로1") || aiResponse.contains("경로 1")) {
            return routes.get(0).getRouteId();
        } else if (aiResponse.contains("경로2") || aiResponse.contains("경로 2")) {
            return routes.get(1).getRouteId();
        } else if (aiResponse.contains("경로3") || aiResponse.contains("경로 3")) {
            return routes.get(2).getRouteId();
        }

        log.warn("AI 응답 파싱 실패, CPTED 최고점으로 폴백");
        return routes.stream()
                .max((a, b) -> Double.compare(a.getCptedAvg(), b.getCptedAvg()))
                .map(RouteAnalysisData::getRouteId)
                .orElse(routes.get(0).getRouteId());
    }

    private List<String> parsePreviewLines(String aiText) {
        List<String> lines = new java.util.ArrayList<>();
        for (String line : aiText.split("\n")) {
            line = line.trim().replaceAll("^[0-9]+\\.\\s*", "");
            if (!line.isEmpty()) {
                lines.add(line);
            }
            if (lines.size() >= 3) break;
        }
        return lines.isEmpty() ? List.of("안전 분석 완료") : lines;
    }

    // 기본 답변
    private List<String> generateDefaultPreview(RouteAnalysisData route) {
        List<String> preview = new ArrayList<>();

        double avg = route.getCptedAvg();
        int risk = route.getRiskSegmentCount();
        int cctv = route.getCctvCount();
        int light = route.getLightCount();
        int brightnessScore = light + (int) Math.round(cctv * 0.5);

        // 1줄: 전체 분위기
        if (avg >= 4.0 && risk <= 1) {
            preview.add("전반적으로 안정감 있는 길이에요");
        } else if (avg >= 3.0) {
            preview.add("대체로 무난하지만 일부 구간은 조심해요");
        } else {
            preview.add("야간에는 특히 주의를 기울이면 좋아요");
        }

        // 2줄: 밝기/시설 장점
        if (brightnessScore >= 10) {
            preview.add("야간에도 비교적 밝아서 안심돼요");
        } else if (brightnessScore >= 5) {
            preview.add("조명은 보통이라 크게 불편하진 않아요");
        } else {
            preview.add("조명이 많지 않아 어두운 곳이 있을 수 있어요");
        }

        // 3줄: 주의점/이용 팁
        if (risk == 0) {
            preview.add("특별한 위험 구간 없이 편하게 걸을 수 있어요");
        } else if (risk <= 2) {
            preview.add("사람이 적은 골목만 조금 서둘러 지나가면 좋아요");
        } else {
            preview.add("여러 구간에서 주변을 자주 둘러보며 이동해 주세요");
        }

        return preview;
    }

    @Async("aiExecutor")
    public CompletableFuture<List<String>> generateRoutePreviewAsync(RouteAnalysisData route) {
        return CompletableFuture.completedFuture(generateRoutePreview(route));
    }

    @Async("aiExecutor")
    public CompletableFuture<String> generateDetailReportAsync(
            String origin,
            String destination,
            RouteAnalysisData analysis,
            ReportResponse.CptedEvaluation eval
    ) {
        try {
            String result = generateDetailReport(origin, destination, analysis, eval);
            return CompletableFuture.completedFuture(result);
        } catch (JsonProcessingException e) {
            log.error("AI 상세 리포트 생성 실패", e);
            return CompletableFuture.completedFuture(
                    "AI 리포트 생성 중 오류가 발생했어요. 기본 리포트를 사용합니다."
            );
        }
    }
}
