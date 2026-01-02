package com.pxbt.dev.FibonacciTimeTrader.controller;

import com.pxbt.dev.FibonacciTimeTrader.model.FibonacciTimeProjection;
import com.pxbt.dev.FibonacciTimeTrader.model.GannDate;
import com.pxbt.dev.FibonacciTimeTrader.model.VortexAnalysis;
import com.pxbt.dev.FibonacciTimeTrader.service.BacktestService;
import com.pxbt.dev.FibonacciTimeTrader.service.BinanceHistoricalService;
import com.pxbt.dev.FibonacciTimeTrader.service.TimeGeometryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/backtest")
public class BacktestController {

    @Autowired
    private BacktestService backtestService;

    @Autowired
    BinanceHistoricalService binanceHistoricalService;

    @Autowired
    TimeGeometryService timeGeometryService;;

    @Autowired
    GannDateController gannDateController;


    @GetMapping("/fibonacci/{symbol}")
    public ResponseEntity<?> getFibonacciBacktest(@PathVariable String symbol) {
        try {
            BacktestService.FibonacciPerformance performance =
                    backtestService.backtestFibonacciProjections(symbol);

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("timestamp", System.currentTimeMillis());
            response.put("performance", performance);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to get Fibonacci backtest", "message", e.getMessage()));
        }
    }

    @GetMapping("/gann/{symbol}")
    public ResponseEntity<?> getGannBacktest(@PathVariable String symbol) {
        try {
            BacktestService.GannPerformance performance =
                    backtestService.backtestGannAnniversaries(symbol);

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("timestamp", System.currentTimeMillis());
            response.put("performance", performance);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to get Gann backtest", "message", e.getMessage()));
        }
    }

    @GetMapping("/confluence/{symbol}")
    public ResponseEntity<?> getConfluenceBacktest(@PathVariable String symbol) {
        try {
            BacktestService.ConfluencePerformance performance =
                    backtestService.backtestConfluenceWindows(symbol);

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("timestamp", System.currentTimeMillis());
            response.put("performance", performance);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to get confluence backtest", "message", e.getMessage()));
        }
    }

    @GetMapping("/solar/{symbol}")
    public ResponseEntity<?> getSolarBacktest(@PathVariable String symbol) {
        try {
            BacktestService.SolarImpactAnalysis analysis =
                    backtestService.analyzeSolarImpact(symbol);

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("timestamp", System.currentTimeMillis());
            response.put("analysis", analysis);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to get solar impact analysis", "message", e.getMessage()));
        }
    }

    @GetMapping("/comprehensive/{symbol}")
    public ResponseEntity<?> comprehensiveBacktest(@PathVariable String symbol) {
        try {
            Map<String, Object> results = new HashMap<>();
            results.put("fibonacci", backtestService.backtestFibonacciProjections(symbol));
            results.put("gann", backtestService.backtestGannAnniversaries(symbol));
            results.put("confluence", backtestService.backtestConfluenceWindows(symbol));
            results.put("solar", backtestService.analyzeSolarImpact(symbol));

            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Comprehensive backtest failed", "message", e.getMessage()));
        }
    }

    // Simple hit analysis - just returns basic statistics without complex logic
    @GetMapping("/fibonacci-hits/{symbol}")
    public ResponseEntity<?> getFibonacciHits(@PathVariable String symbol) {
        try {
            BacktestService.FibonacciPerformance performance =
                    backtestService.backtestFibonacciProjections(symbol);

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("timestamp", System.currentTimeMillis());

            if (performance != null && performance.getFibonacciStats() != null) {
                // Calculate simple statistics
                Map<Double, BacktestService.FibStats> stats = performance.getFibonacciStats();
                long totalSamples = stats.values().stream()
                        .mapToLong(BacktestService.FibStats::getSampleSize)
                        .sum();
                double avgSuccessRate = stats.values().stream()
                        .mapToDouble(BacktestService.FibStats::getSuccessRate)
                        .average()
                        .orElse(0);

                response.put("totalRatiosTested", stats.size());
                response.put("totalSamples", totalSamples);
                response.put("averageSuccessRate", avgSuccessRate);
                response.put("ratios", stats.keySet());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to analyze Fibonacci hits", "message", e.getMessage()));
        }
    }

    @GetMapping("/actual-events/{symbol}")
    public ResponseEntity<?> getActualMarketEvents(@PathVariable String symbol,
                                                   @RequestParam(defaultValue = "60") int lookbackDays) {
        try {
            List<Map<String, Object>> events = new ArrayList<>();

            // Get historical data
            List<BinanceHistoricalService.OHLCData> historicalData =
                    binanceHistoricalService.getHistoricalData(symbol);

            if (historicalData == null || historicalData.size() < lookbackDays) {
                return ResponseEntity.ok(Map.of("symbol", symbol, "events", events, "message", "Insufficient data"));
            }

            // Get Gann dates
            List<GannDate> gannDates = gannDateController.getGannDatesInternal(symbol);

            // Get Fibonacci projections from TimeGeometryService
            VortexAnalysis analysis = timeGeometryService.analyzeSymbol(symbol);
            List<FibonacciTimeProjection> fibProjections = analysis.getFibonacciTimeProjections();

            // Analyze each Gann date
            for (GannDate gann : gannDates) {
                LocalDate gannDate = gann.getDate();

                // Skip future dates
                if (gannDate.isAfter(LocalDate.now())) continue;

                // Find what happened on that date
                Map<String, Object> event = analyzeDateEvent(gannDate, historicalData, "GANN", gann);
                if (event != null) {
                    events.add(event);
                }
            }

            // Analyze each Fibonacci projection date
            if (fibProjections != null) {
                for (FibonacciTimeProjection fib : fibProjections) {
                    LocalDate fibDate = fib.getDate();

                    // Skip future dates
                    if (fibDate.isAfter(LocalDate.now())) continue;

                    // Find what happened on that date
                    Map<String, Object> event = analyzeDateEvent(fibDate, historicalData, "FIBONACCI", fib);
                    if (event != null) {
                        events.add(event);
                    }
                }
            }

            // Sort by date (most recent first)
            events.sort((a, b) -> ((LocalDate) b.get("date")).compareTo((LocalDate) a.get("date")));

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("totalEvents", events.size());
            response.put("events", events);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to get market events", "message", e.getMessage()));
        }
    }

    private Map<String, Object> analyzeDateEvent(LocalDate targetDate,
                                                 List<BinanceHistoricalService.OHLCData> historicalData,
                                                 String signalType,
                                                 Object signal) {

        // Find the data for this date
        for (int i = 0; i < historicalData.size(); i++) {
            BinanceHistoricalService.OHLCData data = historicalData.get(i);
            LocalDate dataDate = Instant.ofEpochMilli(data.timestamp())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();

            if (dataDate.equals(targetDate)) {
                Map<String, Object> event = new HashMap<>();
                event.put("date", targetDate);
                event.put("signalType", signalType);
                event.put("price", data.close());

                // Get signal info
                if (signal instanceof GannDate) {
                    GannDate gann = (GannDate) signal;
                    event.put("signalDetails", Map.of(
                            "period", gann.getType(),
                            "sourcePivot", gann.getSourcePivot()
                    ));
                } else if (signal instanceof FibonacciTimeProjection) {
                    FibonacciTimeProjection fib = (FibonacciTimeProjection) signal;
                    event.put("signalDetails", Map.of(
                            "ratio", fib.getFibonacciRatio(),
                            "days", fib.getFibonacciNumber(),
                            "description", fib.getDescription()
                    ));
                }

                // Calculate 1-day change
                if (i > 0) {
                    BinanceHistoricalService.OHLCData prevData = historicalData.get(i - 1);
                    double priceChange = ((data.close() - prevData.close()) / prevData.close()) * 100;
                    event.put("dailyChangePercent", priceChange);
                    event.put("direction", priceChange > 0 ? "UP" : priceChange < 0 ? "DOWN" : "FLAT");
                    event.put("dailyChange", priceChange);
                }

                // Calculate 3-day change (i+2 if possible)
                if (i + 2 < historicalData.size()) {
                    BinanceHistoricalService.OHLCData threeDayData = historicalData.get(i + 2);
                    double threeDayChange = ((threeDayData.close() - data.close()) / data.close()) * 100;
                    event.put("threeDayChangePercent", threeDayChange);
                }

                // Calculate weekly high/low range
                int weekStart = Math.max(0, i - 3);
                int weekEnd = Math.min(historicalData.size() - 1, i + 3);
                double weekHigh = Double.MIN_VALUE;
                double weekLow = Double.MAX_VALUE;

                for (int j = weekStart; j <= weekEnd; j++) {
                    weekHigh = Math.max(weekHigh, historicalData.get(j).high());
                    weekLow = Math.min(weekLow, historicalData.get(j).low());
                }

                event.put("weekHigh", weekHigh);
                event.put("weekLow", weekLow);
                event.put("weekRange", ((weekHigh - weekLow) / data.close()) * 100);

                return event;
            }
        }

        return null;
    }
}