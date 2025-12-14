package com.pxbt.dev.FibonacciTimeTrader.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
public class WebClientConfig {

    // General purpose WebClient (no base URL)
    @Bean
    public WebClient generalWebClient() {
        return WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // NOAA-specific WebClient
    @Bean("noaaWebClient")
    public WebClient noaaWebClient() {
        return WebClient.builder()
                .baseUrl("https://services.swpc.noaa.gov")
                .defaultHeader(HttpHeaders.USER_AGENT, "FibonacciTimeTrader/1.0 (contact@yourdomain.com)")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/plain")
                .filter(ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
                    log.debug("NOAA Request: {} {}", clientRequest.method(), clientRequest.url());
                    return Mono.just(clientRequest);
                }))
                .build();
    }
}
