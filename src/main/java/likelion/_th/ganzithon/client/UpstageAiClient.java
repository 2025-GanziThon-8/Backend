package likelion._th.ganzithon.client;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RequiredArgsConstructor
// upstage ai 호출
public class UpstageAiClient {

    @Qualifier("upstageClient")
    private final WebClient webClient;

    // upstage ai 호출
    public String generateText(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "model", "solar-pro2",
                "reasoning_effort", "high",
                "messages", new Object[]{
                        Map.of("role", "user", "content", prompt)
                }
        );

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
