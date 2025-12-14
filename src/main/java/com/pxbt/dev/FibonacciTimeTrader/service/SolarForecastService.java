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
                return getFallbackForecast();
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
            return getFallbackForecast();
        }
    }

    public List<ForecastDay> getHighApDates() {
        SolarForecast forecast = getSolarForecast();
        return forecast != null && forecast.getForecast() != null
                ? forecast.getForecast()
                : new ArrayList<>();
    }

    private SolarForecast getFallbackForecast() {
        log.warn("Using fallback data");
        SolarForecast forecast = new SolarForecast();
        forecast.setTimestamp(System.currentTimeMillis());
        forecast.setMessage("Fallback - NOAA unavailable");
        forecast.setSource("Fallback");

        // Simple fallback with correct AP values
        List<ForecastDay> days = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // Create realistic AP values from your actual data
        int[] sampleAPs = {70, 33, 12, 12, 15, 20, 20, 25, 20, 20, 15, 30, 25, 18, 70, 33};

        for (int i = 0; i < Math.min(45, sampleAPs.length); i++) {
            ForecastDay day = new ForecastDay();
            day.setDate(today.plusDays(i));
            day.setAp(sampleAPs[i % sampleAPs.length]);
            days.add(day);
        }

        forecast.setForecast(days);
        forecast.setCurrentAp(days.get(0).getAp());

        return forecast;
    }
}