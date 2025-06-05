package com.nklcbdty.api.crawler.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    @Value("${google.gemini.api-key}")
    private String apiKey;

    @Value("${google.gemini.api-url}")
    private String apiUrl;

    @Bean
    public WebClient geminiWebClient() {
        return WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // API 키는 URL 파라미터로 추가하는 경우가 많습니다.
                // .defaultHeader("Authorization", "Bearer " + apiKey) // 만약 헤더에 넣는 방식이라면 이렇게 설정
                .build();
    }
}
