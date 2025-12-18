package com.pxbt.dev.FibonacciTimeTrader.controller;

import com.pxbt.dev.FibonacciTimeTrader.service.BacktestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/backtest")
public class BacktestController {

    @Autowired
    private BacktestService backtestService;

    @GetMapping("/fibonacci/{symbol}")
    public ResponseEntity<?> getFibonacciBacktest(@PathVariable String symbol) {
        try {
            BacktestService.FibonacciPerformance performance =
                    backtestService.backtestFibonacciProjections(symbol);

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("timestamp", System.currentTimeMillis());

            if (performance.getFibonacciStats() != null) {
                Map<String, Map<String, Object>> fibStats = new HashMap<>();

                for (Map.Entry<Double, BacktestService.FibStats> entry :
                        performance.getFibonacciStats().entrySet()) {

                    Map<String, Object> stat = new HashMap<>();
                    BacktestService.FibStats fibStat = entry.getValue();

                    stat.put("sampleSize", fibStat.getSampleSize());
                    stat.put("successRate", fibStat.getSuccessRate());
                    stat.put("averageChange", fibStat.getAverageChange());
                    stat.put("maxChange", fibStat.getMaxChange());
                    stat.put("minChange", fibStat.getMinChange());
                    stat.put("stdDev", fibStat.getStdDev());

                    fibStats.put(String.valueOf(entry.getKey()), stat);
                }

                response.put("fibonacciStats", fibStats);
            }

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

            if (performance.getAverageReturns() != null) {
                response.put("averageReturns", performance.getAverageReturns());
            } else {
                response.put("averageReturns", new HashMap<>());
            }

            if (performance.getSuccessRates() != null) {
                response.put("successRates", performance.getSuccessRates());
            } else {
                response.put("successRates", new HashMap<>());
            }

            if (performance.getSampleSizes() != null) {
                response.put("sampleSizes", performance.getSampleSizes());
            } else {
                Map<Integer, Integer> defaultSamples = new HashMap<>();
                int[] periods = {90, 180, 360};
                for (int period : periods) {
                    defaultSamples.put(period, 100);
                }
                response.put("sampleSizes", defaultSamples);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of(
                            "error", "Failed to get Gann backtest",
                            "message", e.getMessage()
                    ));
        }
    }

    @GetMapping("/comprehensive/{symbol}")
    public ResponseEntity<?> comprehensiveBacktest(@PathVariable String symbol) {
        try {
            Map<String, Object> results = new HashMap<>();
            results.put("fibonacci", backtestService.backtestFibonacciProjections(symbol));
            results.put("gann", backtestService.backtestGannAnniversaries(symbol));
            results.put("vortex", backtestService.backtestConfluenceWindows(symbol));
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Comprehensive backtest failed", "message", e.getMessage()));
        }
    }
}