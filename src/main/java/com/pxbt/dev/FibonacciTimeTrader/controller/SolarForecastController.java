package com.pxbt.dev.FibonacciTimeTrader.controller;

import com.pxbt.dev.FibonacciTimeTrader.model.ForecastDay;
import com.pxbt.dev.FibonacciTimeTrader.model.SolarForecast;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/solar")
@RequiredArgsConstructor
public class SolarForecastController {

    private final com.pxbt.dev.FibonacciTimeTrader.service.SolarForecastService solarForecastService;

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

}