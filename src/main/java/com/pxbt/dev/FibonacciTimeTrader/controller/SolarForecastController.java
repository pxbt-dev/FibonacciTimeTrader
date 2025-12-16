package com.pxbt.dev.FibonacciTimeTrader.controller;

import com.pxbt.dev.FibonacciTimeTrader.Gateway.NoaaGateway;
import com.pxbt.dev.FibonacciTimeTrader.model.ForecastDay;
import com.pxbt.dev.FibonacciTimeTrader.model.SolarForecast;
import com.pxbt.dev.FibonacciTimeTrader.service.NoaaParserService;
import com.pxbt.dev.FibonacciTimeTrader.service.SolarForecastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/solar")
@RequiredArgsConstructor
public class SolarForecastController {

    private final com.pxbt.dev.FibonacciTimeTrader.service.SolarForecastService solarForecastService;

    @Autowired
    NoaaGateway noaaGateway;
    @Autowired
    private NoaaParserService noaaParserService;

    @GetMapping("/forecast")
    public ResponseEntity<SolarForecast> getSolarForecast() {
        log.debug("GET /api/solar/forecast");
        try {
            // Returns 45-day forecast (AP ≥ 12 only)
            SolarForecast forecast = solarForecastService.getSolarForecast();
            return ResponseEntity.ok(forecast);
        } catch (Exception e) {
            log.error("Failed to get solar forecast", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/high-ap-dates")
    public ResponseEntity<List<ForecastDay>> getHighApDates() {
        log.debug("GET /api/solar/high-ap-dates");  // ✅ Updated endpoint name
        try {
            List<ForecastDay> highApDates = solarForecastService.getHighApDates();
            return ResponseEntity.ok(highApDates);
        } catch (Exception e) {
            log.error("Failed to get high AP dates", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/debug/solar/raw")
    public ResponseEntity<?> debugSolarRaw() {
        try {
            // Get solar service (you'll need to inject this properly)
            SolarForecastService solarService = new SolarForecastService(noaaGateway, noaaParserService);
            SolarForecast forecast = solarService.getSolarForecast();

            Map<String, Object> response = new HashMap<>();
            response.put("timestamp", System.currentTimeMillis());
            response.put("source", forecast.getSource());
            response.put("message", forecast.getMessage());
            response.put("totalDays", forecast.getForecast() != null ? forecast.getForecast().size() : 0);

            if (forecast.getForecast() != null) {
                // Check for duplicates
                Map<LocalDate, Long> dateCounts = forecast.getForecast().stream()
                        .collect(Collectors.groupingBy(ForecastDay::getDate, Collectors.counting()));

                List<Map<String, Object>> duplicates = dateCounts.entrySet().stream()
                        .filter(entry -> entry.getValue() > 1)
                        .map(entry -> {
                            Map<String, Object> dup = new HashMap<>();
                            dup.put("date", entry.getKey());
                            dup.put("count", entry.getValue());

                            // Get all AP values for this date
                            List<Integer> apValues = forecast.getForecast().stream()
                                    .filter(day -> day.getDate().equals(entry.getKey()))
                                    .map(ForecastDay::getAp)
                                    .collect(Collectors.toList());
                            dup.put("apValues", apValues);

                            return dup;
                        })
                        .collect(Collectors.toList());

                response.put("duplicates", duplicates);
                response.put("hasDuplicates", !duplicates.isEmpty());

                // Show first 10 days
                List<Map<String, Object>> preview = forecast.getForecast().stream()
                        .limit(10)
                        .map(day -> {
                            Map<String, Object> dayMap = new HashMap<>();
                            dayMap.put("date", day.getDate());
                            dayMap.put("ap", day.getAp());
                            return dayMap;
                        })
                        .collect(Collectors.toList());
                response.put("preview", preview);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/debug/solar/test-parsing")
    public ResponseEntity<?> testSolarParsing() {
        try {
            // Test date parsing
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMMyy", Locale.US);

            Map<String, Object> testResults = new HashMap<>();

            // Test the exact dates from your data
            String[] testDates = {"21Dec25", "22Dec25", "23Dec25", "24Dec25"};
            List<Map<String, Object>> parsingTests = new ArrayList<>();

            for (String dateStr : testDates) {
                try {
                    LocalDate parsed = LocalDate.parse(dateStr, formatter);
                    Map<String, Object> test = new HashMap<>();
                    test.put("input", dateStr);
                    test.put("parsed", parsed.toString());
                    test.put("year", parsed.getYear());
                    test.put("month", parsed.getMonthValue());
                    test.put("day", parsed.getDayOfMonth());
                    parsingTests.add(test);
                } catch (Exception e) {
                    Map<String, Object> test = new HashMap<>();
                    test.put("input", dateStr);
                    test.put("error", e.getMessage());
                    parsingTests.add(test);
                }
            }

            testResults.put("parsingTests", parsingTests);

            // Also test the actual solar data
            List<ForecastDay> solarDays = solarForecastService.getHighApDates();
            List<Map<String, Object>> actualDates = solarDays.stream()
                    .map(day -> {
                        Map<String, Object> dateInfo = new HashMap<>();
                        dateInfo.put("date", day.getDate());
                        dateInfo.put("dateString", day.getDate().toString());
                        dateInfo.put("ap", day.getAp());
                        dateInfo.put("year", day.getDate().getYear());
                        return dateInfo;
                    })
                    .filter(info -> {
                        LocalDate date = (LocalDate) info.get("date");
                        return date.getYear() == 2025 || date.getYear() == 2026;
                    })
                    .limit(10)
                    .collect(Collectors.toList());

            testResults.put("actualSolarDates", actualDates);

            return ResponseEntity.ok(testResults);

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage()));
        }
    }

}