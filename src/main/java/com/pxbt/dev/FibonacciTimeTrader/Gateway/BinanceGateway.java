package com.pxbt.dev.FibonacciTimeTrader.Gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;


@Slf4j
@Service
public class BinanceGateway {

    private final WebClient webClient;

    @Value("${binance.api.klines-endpoint}")
    private String binanceKlinesEndpoint;

    @Value("${binance.api.klines-query-params}")
    private String klinesQueryParams;

    public BinanceGateway(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<String> getRawKlines(String symbol, String interval, int limit) {
        String binanceSymbol = symbol.toUpperCase() + "USDT";

        log.debug("Fetching Binance klines for {} with interval {}", symbol, interval);

        return webClient.get()
                .uri(binanceKlinesEndpoint + klinesQueryParams, binanceSymbol, interval, limit)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> log.debug("Binance raw data received for {}", symbol))
                .doOnError(error -> log.error("Binance API failed for {}: {}", symbol, error.getMessage()));
    }
}