package com.pxbt.dev.FibonacciTimeTrader.Gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class NoaaGateway {

    private final WebClient webClient;

    @Value("${noaa.solar.forecast-url}")
    private String solarForecastUrl;

    public NoaaGateway(@Qualifier("noaaWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<String> getRawSolarForecast() {
        log.debug("Fetching solar forecast from NOAA");

        return webClient.get()
                .uri(solarForecastUrl)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> log.debug("NOAA solar forecast received"))
                .doOnError(error -> log.error("NOAA solar forecast failed: {}", error.getMessage()));
    }
}