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
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
// upstage ai 호출
public class UpstageAiClient {

    @Qualifier("upstageClient")
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // AI가 3개 경로 중 추천 경로 선택
    public String selectRecommendedRoute(List<RouteAnalysisData> routes) {
        if(routes== null || routes.size() < 3) {
            // 경로가 없거나, 3개 미만 -> 첫번째 경로 반환
            return routes.get(0).getRouteId();
        }

        try {
            String prompt = buildRouteComparisonPrompt(routes); // 프롬프트
            String response = generateText(prompt);

            return parseRecommendedRouteId(response, routes);
        } catch (Exception e) {
            return routes.stream()
                    .max((a,b) -> Double.compare(a.getCptedAvg(), b.getCptedAvg()))
                    .map(RouteAnalysisData::getRouteId)
                    .orElse(routes.get(0).getRouteId());
        }
    }
    
    // /paths: 경로별 AI 프리뷰 생성 (1-3줄)
    public List<String> generateRoutePreview(RouteAnalysisData route) {
        try {
            String prompt = String.format(
                    "다음 도보 경로의 특징을 3줄로 간단히 요약해주세요:\n" +
                            "- CPTED 점수: %.1f점\n" +
                            "- CCTV: %d개, 가로등: %d개\n" +
                            "- 위험 구간: %d개\n\n" +
                            "1. 2. 3. 형식으로 각 줄을 20자 이내로 작성해주세요.",
                    route.getCptedAvg(),
                    route.getCctvCount(),
                    route.getLightCount(),
                    route.getRiskSegmentCount()
            );

            String response = generateText(prompt);
            return parsePreviewLines(response);
        } catch (Exception e) {
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
                "reasoning_effort", "high",
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

    private String buildRouteComparisonPrompt(
            List<RouteAnalysisData> routes
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음 3개의 도보 경로를 종합하여 가장 추천할 경로를 선택해줘");

        for (int i = 0; i < routes.size(); i++) {
            RouteAnalysisData route = routes.get(i);
            sb.append(String.format(" [경로%d] \n", i+1));
            sb.append(String.format("- 거리: %dm (도보 약 %d분)\n", route.getDistance(), route.getTime() / 60));
            sb.append(String.format("- CPTED 안전 점수: %.1f점\n", route.getCptedAvg()));
            sb.append(String.format("- 안전 시설: CCTV %d개, 가로등 %d개\n",
                    route.getCctvCount(), route.getLightCount()));
            sb.append(String.format("- 주의/위험 구간: %d개\n\n", route.getRiskSegmentCount()));
        }
        sb.append("다음 형식으로 답변해주세요:\n");
        sb.append("추천 경로: 경로N\n");
        sb.append("이유:\n");
        sb.append("1. [첫 번째 이유]\n");
        sb.append("2. [두 번째 이유]\n");
        sb.append("3. [세 번째 이유]");

        return sb.toString();
    }

    private String buildDetailedPrompt(String origin, String destination
    , RouteAnalysisData analysis, ReportResponse.CptedEvaluation cptedEval) {
        return String.format(
                "【경로 정보】\n" +
                        "%s → %s (도보 %dm, 약 %d분)\n\n" +
                        "【CPTED 5대 평가】\n" +
                        "1. 자연감시: %d점 - %s\n" +
                        "2. 접근통제: %d점 - %s\n" +
                        "3. 영역성: %d점 - %s\n" +
                        "4. 활동성: %d점 - %s\n" +
                        "5. 유지관리: %d점 - %s\n\n" +
                        "【안전 시설】\n" +
                        "- CCTV: %d개, 가로등: %d개\n" +
                        "- 주의 구간: %d개\n\n" +
                        "위 데이터를 바탕으로 이 통학로의 안전성을 3-4줄로 간결하게 종합 평가해주세요.",
                origin, destination, analysis.getDistance(), analysis.getTime() / 60,
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
        List<String> preview = new java.util.ArrayList<>();

        if (route.getCptedAvg() >= 3.0) {
            preview.add("안전 시설이 잘 갖춰져 있습니다");
        } else {
            preview.add("일부 구간 주의가 필요합니다");
        }

        if (route.getCctvCount() >= 5) {
            preview.add("CCTV가 충분히 설치되어 있습니다");
        } else {
            preview.add("CCTV 설치가 제한적입니다");
        }

        if (route.getRiskSegmentCount() == 0) {
            preview.add("전 구간 안전합니다");
        } else {
            preview.add(String.format("%d개 구간에서 주의하세요", route.getRiskSegmentCount()));
        }

        return preview;
    }

}
