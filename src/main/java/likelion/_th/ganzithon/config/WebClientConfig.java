package likelion._th.ganzithon.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
// kakao, upstage api 호출
public class WebClientConfig {

    @Value("${external-api.tmap.base-url}")
    private String tmapBaseUrl;

    @Value("${external-api.upstage.base-url}")
    private String upstageBaseUrl;

    @Value("${external-api.google.base-url}")
    private String googleMapBaseUrl;

    @Bean(name = "tmapClient")
    public WebClient tmapClient(@Value("${external-api.tmap.api-key}") String apiKey) {
        return WebClient.builder()
                .baseUrl(tmapBaseUrl)
                .defaultHeader("appKey", apiKey)
                .build();
    }

    @Bean(name = "googleMapClient")
    public WebClient googleMapClient() {
        return WebClient.builder()
                .baseUrl(googleMapBaseUrl)
                .build();
    }

    @Bean(name = "upstageClient")
    public WebClient upstageClient(@Value("${external-api.upstage.api-key}") String apiKey) {
        return WebClient.builder()
                .baseUrl(upstageBaseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

}
