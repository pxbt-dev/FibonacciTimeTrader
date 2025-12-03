package com.pxbt.dev.FibonacciTimeTrader.controller;

import com.pxbt.dev.FibonacciTimeTrader.service.BacktestService;
import org.springframework.beans.factory.annotation.Autowired;
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
    public BacktestService.FibonacciPerformance backtestFibonacci(@PathVariable String symbol) {
        return backtestService.backtestFibonacciProjections(symbol);
    }

    @GetMapping("/gann/{symbol}")
    public BacktestService.GannPerformance backtestGann(@PathVariable String symbol) {
        return backtestService.backtestGannAnniversaries(symbol);
    }

    @GetMapping("/comprehensive/{symbol}")
    public Map<String, Object> comprehensiveBacktest(@PathVariable String symbol) {
        Map<String, Object> results = new HashMap<>();
        results.put("fibonacci", backtestService.backtestFibonacciProjections(symbol));
        results.put("gann", backtestService.backtestGannAnniversaries(symbol));
        results.put("vortex", backtestService.backtestVortexWindows(symbol));
        return results;
    }
}