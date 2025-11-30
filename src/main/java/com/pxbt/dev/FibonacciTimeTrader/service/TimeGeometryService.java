package com.pxbt.dev.FibonacciTimeTrader.service;

import com.pxbt.dev.FibonacciTimeTrader.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TimeGeometryService {

    @Autowired
    private BinanceHistoricalService binanceHistoricalService;

    private static final int[] FIBONACCI_SEQUENCE = {5, 8, 13, 21, 34, 55, 89, 144, 233};

    public VortexAnalysis analyzeSymbol(String symbol) {
        log.info("⏰ Starting Time Geometry analysis for {}", symbol);

        // ✅ USE THE SIMPLIFIED BINANCE DATA - Call getHistoricalData()
        List<BinanceHistoricalService.OHLCData> historicalData = binanceHistoricalService.getHistoricalData(symbol);

        VortexAnalysis analysis = new VortexAnalysis();
        analysis.setSymbol(symbol);

        if (historicalData == null || historicalData.isEmpty()) {
            log.warn("⚠️ No historical data available for {}", symbol);
            return analysis;
        }

        log.info("📊 Using {} real Binance data points for {} time geometry",
                historicalData.size(), symbol);

        // Use the data for analysis - pivot detection now uses OHLCData
        List<PricePivot> pivots = findSignificantPivots(historicalData);

        analysis.setFibonacciTimeProjections(calculateFibonacciTimeProjections(pivots));
        analysis.setGannDates(calculateGannDates(pivots));
        analysis.setVortexWindows(identifyVortexWindows(analysis));
        analysis.setCompressionScore(calculateCompressionScore(historicalData));
        analysis.setConfidenceScore(calculateConfidenceScore(analysis));

        log.info("✅ Time Geometry complete: {} projections, {} vortex windows",
                analysis.getFibonacciTimeProjections().size(),
                analysis.getVortexWindows().size());

        return analysis;
    }

    private List<PricePivot> findSignificantPivots(List<BinanceHistoricalService.OHLCData> historicalData) {
        List<PricePivot> pivots = new ArrayList<>();

        if (historicalData.size() < 10) return pivots;

        // Simple pivot detection using OHLC data
        for (int i = 3; i < historicalData.size() - 3; i++) {
            BinanceHistoricalService.OHLCData current = historicalData.get(i);

            // Check for high pivot
            if (current.high() > historicalData.get(i-1).high() &&
                    current.high() > historicalData.get(i-2).high() &&
                    current.high() > historicalData.get(i+1).high() &&
                    current.high() > historicalData.get(i+2).high()) {

                pivots.add(new PricePivot(
                        convertTimestampToDate(current.timestamp()),
                        current.high(),
                        "HIGH",
                        0.7 // Simple strength
                ));
            }

            // Check for low pivot
            if (current.low() < historicalData.get(i-1).low() &&
                    current.low() < historicalData.get(i-2).low() &&
                    current.low() < historicalData.get(i+1).low() &&
                    current.low() < historicalData.get(i+2).low()) {

                pivots.add(new PricePivot(
                        convertTimestampToDate(current.timestamp()),
                        current.low(),
                        "LOW",
                        0.7 // Simple strength
                ));
            }
        }

        return pivots.stream()
                .sorted((a, b) -> b.getDate().compareTo(a.getDate())) // Most recent first
                .limit(10) // Top 10 most recent pivots
                .collect(Collectors.toList());
    }

    private List<FibonacciTimeProjection> calculateFibonacciTimeProjections(List<PricePivot> pivots) {
        List<FibonacciTimeProjection> projections = new ArrayList<>();

        if (pivots.isEmpty()) return projections;

        // Use the most recent pivot for projections
        PricePivot lastPivot = pivots.get(0);

        for (int fib : FIBONACCI_SEQUENCE) {
            LocalDate projectionDate = lastPivot.getDate().plusDays(fib);

            FibonacciTimeProjection projection = new FibonacciTimeProjection();
            projection.setDate(projectionDate);
            projection.setFibonacciNumber(fib);
            projection.setSourcePivot(lastPivot);
            projection.setIntensity(calculateFibIntensity(fib));
            projection.setType(determineProjectionType(lastPivot.getType(), fib));
            projection.setDescription(String.format("F%d from %s pivot at $%.2f",
                    fib, lastPivot.getType().toLowerCase(), lastPivot.getPrice()));

            projections.add(projection);
        }

        return projections;
    }

    private List<GannDate> calculateGannDates(List<PricePivot> pivots) {
        List<GannDate> gannDates = new ArrayList<>();

        for (PricePivot pivot : pivots) {
            // Add Gann anniversaries (90, 180, 360 days)
            gannDates.add(new GannDate(pivot.getDate().plusDays(90), "90D_ANNIVERSARY", pivot));
            gannDates.add(new GannDate(pivot.getDate().plusDays(180), "180D_ANNIVERSARY", pivot));
            gannDates.add(new GannDate(pivot.getDate().plusDays(360), "360D_ANNIVERSARY", pivot));
        }

        return gannDates;
    }

    private double calculateCompressionScore(List<BinanceHistoricalService.OHLCData> historicalData) {
        if (historicalData.size() < 20) return 0.0;

        // Simple volatility calculation using OHLC data
        double totalVolatility = 0;
        int lookback = Math.min(20, historicalData.size());

        for (int i = historicalData.size() - lookback; i < historicalData.size(); i++) {
            BinanceHistoricalService.OHLCData candle = historicalData.get(i);
            double range = (candle.high() - candle.low()) / candle.close();
            totalVolatility += range;
        }

        double avgVolatility = totalVolatility / lookback;

        // Lower volatility = higher compression
        return Math.min(1.0, 1.0 / (avgVolatility + 0.01));
    }

    private List<VortexWindow> identifyVortexWindows(VortexAnalysis analysis) {
        Map<LocalDate, List<String>> dateSignals = new HashMap<>();

        // Aggregate Fibonacci projections
        analysis.getFibonacciTimeProjections().forEach(proj ->
                dateSignals.computeIfAbsent(proj.getDate(), k -> new ArrayList<>())
                        .add("FIB_" + proj.getFibonacciNumber()));

        // Aggregate Gann dates
        analysis.getGannDates().forEach(gann ->
                dateSignals.computeIfAbsent(gann.getDate(), k -> new ArrayList<>())
                        .add(gann.getType()));

        // Create vortex windows where signals converge
        List<VortexWindow> windows = new ArrayList<>();
        for (Map.Entry<LocalDate, List<String>> entry : dateSignals.entrySet()) {
            if (entry.getValue().size() >= 2) {
                VortexWindow window = new VortexWindow();
                window.setDate(entry.getKey());
                window.setIntensity(entry.getValue().size() * 0.3);
                window.setType("TIME_VORTEX");
                window.setContributingFactors(entry.getValue());
                window.setDescription("Multiple time signals converging");
                windows.add(window);
            }
        }

        return windows;
    }

    private double calculateConfidenceScore(VortexAnalysis analysis) {
        if (analysis.getVortexWindows().isEmpty()) return 0.1;

        double avgIntensity = analysis.getVortexWindows().stream()
                .mapToDouble(VortexWindow::getIntensity)
                .average()
                .orElse(0.0);

        return Math.min(0.9, avgIntensity + analysis.getCompressionScore() * 0.3);
    }

    // Helper methods
    private String determineProjectionType(String pivotType, int fibNumber) {
        return pivotType.equals("HIGH") ? "RESISTANCE" : "SUPPORT";
    }

    private double calculateFibIntensity(int fibNumber) {
        return Math.min(1.0, fibNumber / 100.0);
    }

    private LocalDate convertTimestampToDate(long timestamp) {
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate();
    }
}