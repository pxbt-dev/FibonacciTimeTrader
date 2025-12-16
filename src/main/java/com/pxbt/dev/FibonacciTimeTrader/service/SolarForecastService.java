package com.pxbt.dev.FibonacciTimeTrader.service;

import com.pxbt.dev.FibonacciTimeTrader.Gateway.NoaaGateway;
import com.pxbt.dev.FibonacciTimeTrader.model.ForecastDay;
import com.pxbt.dev.FibonacciTimeTrader.model.SolarForecast;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class SolarForecastService {
    private final NoaaGateway noaaGateway;
    private final NoaaParserService parserService;
    private SolarForecast cachedForecast;
    private Instant lastFetchTime;
    private static final Duration CACHE_DURATION = Duration.ofHours(6);

    public SolarForecastService(NoaaGateway noaaGateway, NoaaParserService parserService) {
        this.noaaGateway = noaaGateway;
        this.parserService = parserService;
    }

    /**
     * Get 45-day AP forecast (AP ≥ 12 only)
     */
    public SolarForecast getSolarForecast() {
        log.info("=== GET SOLAR FORECAST ===");

        // Check cache
        if (cachedForecast != null && lastFetchTime != null &&
                Duration.between(lastFetchTime, Instant.now()).compareTo(CACHE_DURATION) < 0) {
            log.info("Returning cached forecast");
            return cachedForecast;
        }

        try {
            // Fetch from NOAA
            log.info("Fetching from NOAA...");
            String forecastText = noaaGateway.getRawSolarForecast()
                    .doOnError(error -> log.error("Fetch failed: {}", error.getMessage()))
                    .onErrorReturn("ERROR")
                    .block(Duration.ofSeconds(10));

            if ("ERROR".equals(forecastText) || forecastText == null || forecastText.trim().isEmpty()) {
                log.error("NOAA fetch failed or returned empty");
                return null;
            }

            // Parse (AP ≥ 12 only)
            log.info("Parsing forecast...");
            List<ForecastDay> forecastDays = parserService.parseSolarForecast(forecastText);

            // Build response
            SolarForecast forecast = new SolarForecast();
            forecast.setTimestamp(System.currentTimeMillis());
            forecast.setForecast(forecastDays);
            forecast.setIssueDate(parserService.parseIssueDate(forecastText));
            forecast.setSource("NOAA 45-day AP Forecast");

            // Set current AP from today or first day
            Optional<ForecastDay> today = forecastDays.stream()
                    .filter(day -> day.getDate().isEqual(LocalDate.now()))
                    .findFirst();

            if (today.isPresent()) {
                forecast.setCurrentAp(today.get().getAp());
            } else if (!forecastDays.isEmpty()) {
                forecast.setCurrentAp(forecastDays.get(0).getAp());
            }

            // Cache
            cachedForecast = forecast;
            lastFetchTime = Instant.now();

            log.info("Success! Found {} days with AP ≥ 12", forecastDays.size());
            return forecast;

        } catch (Exception e) {
            log.error("Critical error: {}", e.getMessage(), e);
            return null;
        }
    }

    public List<ForecastDay> getHighApDates() {
        SolarForecast forecast = getSolarForecast();
        return forecast != null && forecast.getForecast() != null
                ? forecast.getForecast()
                : new ArrayList<>();
    }


}