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

    private static final double[] FIBONACCI_RATIOS = {
            0.382, 0.500, 0.618, 0.786,  // Retracements
            1.000, 1.272, 1.618, 2.618   // Extensions
    };

    public VortexAnalysis analyzeSymbol(String symbol) {
        log.info("⏰ Starting EXTENDED Time Geometry analysis for {}", symbol);

        List<BinanceHistoricalService.OHLCData> historicalData = binanceHistoricalService.getHistoricalData(symbol);

        VortexAnalysis analysis = new VortexAnalysis();
        analysis.setSymbol(symbol);

        if (historicalData == null || historicalData.isEmpty()) {
            log.warn("⚠️ No historical data available for {}", symbol);
            return analysis;
        }

        log.info("📊 Using {} extended data points for {} ({} years)",
                historicalData.size(), symbol, historicalData.size() / 365);

        // Use weekly data for major cycle detection
        List<BinanceHistoricalService.OHLCData> weeklyData = binanceHistoricalService.getWeeklyData(symbol);

        List<PricePivot> majorPivots = findMajorCyclePivots(weeklyData);
        List<PricePivot> recentPivots = findRecentPivots(historicalData);

        List<PricePivot> allPivots = new ArrayList<>();
        allPivots.addAll(majorPivots);
        allPivots.addAll(recentPivots);

        log.info("🎯 Found {} major + {} recent pivots for {}",
                majorPivots.size(), recentPivots.size(), symbol);

        // Use calculateExtendedProjections
        analysis.setFibonacciTimeProjections(calculateExtendedProjections(allPivots));
        analysis.setGannDates(calculateGannDates(allPivots));
        analysis.setVortexWindows(identifyVortexWindows(analysis));
        analysis.setCompressionScore(calculateCompressionScore(historicalData));
        analysis.setConfidenceScore(calculateConfidenceScore(analysis));

        log.info("✅ EXTENDED Time Geometry complete: {} projections, {} vortex windows",
                analysis.getFibonacciTimeProjections().size(),
                analysis.getVortexWindows().size());

        return analysis;
    }

    /**
     * Find major cycle pivots from weekly data
     * Uses larger window for detecting significant highs/lows
     */
    private List<PricePivot> findMajorCyclePivots(List<BinanceHistoricalService.OHLCData> weeklyData) {
        List<PricePivot> majorPivots = new ArrayList<>();

        if (weeklyData == null || weeklyData.size() < 40) {
            log.warn("Insufficient weekly data for major pivot detection: {} points",
                    weeklyData != null ? weeklyData.size() : 0);
            return majorPivots;
        }

        // Larger window for major cycles (approx 6 months in weekly data)
        int majorWindow = 26;  // ~6 months

        for (int i = majorWindow; i < weeklyData.size() - majorWindow; i++) {
            BinanceHistoricalService.OHLCData current = weeklyData.get(i);
            boolean isMajorHigh = true;
            boolean isMajorLow = true;

            // Check surrounding window for major high
            for (int j = i - majorWindow; j <= i + majorWindow; j++) {
                if (j == i) continue;

                BinanceHistoricalService.OHLCData compare = weeklyData.get(j);

                if (compare.high() >= current.high()) {
                    isMajorHigh = false;
                }
                if (compare.low() <= current.low()) {
                    isMajorLow = false;
                }

                // Early exit if both false
                if (!isMajorHigh && !isMajorLow) break;
            }

            if (isMajorHigh) {
                PricePivot pivot = new PricePivot(
                        convertTimestampToDate(current.timestamp()),
                        current.high(),
                        "MAJOR_HIGH",
                        0.95  // Very high strength for major weekly pivots
                );
                majorPivots.add(pivot);

                log.debug("🎯 MAJOR WEEKLY HIGH: {} at ${}",
                        pivot.getDate(), pivot.getPrice());
            }

            if (isMajorLow) {
                PricePivot pivot = new PricePivot(
                        convertTimestampToDate(current.timestamp()),
                        current.low(),
                        "MAJOR_LOW",
                        0.95
                );
                majorPivots.add(pivot);

                log.debug("🎯 MAJOR WEEKLY LOW: {} at ${}",
                        pivot.getDate(), pivot.getPrice());
            }
        }

        log.info("Found {} major cycle pivots from weekly data", majorPivots.size());
        return majorPivots;
    }

    private List<FibonacciTimeProjection> calculateExtendedProjections(List<PricePivot> allPivots) {
        List<FibonacciTimeProjection> projections = new ArrayList<>();

        if (allPivots.isEmpty()) return projections;

        for (PricePivot pivot : allPivots) {
            for (double ratio : FIBONACCI_RATIOS) {
                // Calculate exact days (don't round for storage)
                double exactDays = 100 * ratio; // 0.786 → 78.6
                int displayDays = (int) Math.round(exactDays); // 78.6 → 79 (for display only)

                LocalDate projectionDate = pivot.getDate().plusDays(displayDays);

                if (!projectionDate.isBefore(LocalDate.now())) {
                    FibonacciTimeProjection projection = new FibonacciTimeProjection();
                    projection.setDate(projectionDate);
                    projection.setFibonacciNumber(displayDays); // Rounded for display
                    projection.setFibonacciRatio(ratio); // ✅ Store the ACTUAL ratio (0.786)
                    projection.setSourcePivot(pivot);
                    projection.setIntensity(calculateFibIntensity(ratio) * pivot.getStrength());
                    projection.setType(determineProjectionType(pivot.getType(), ratio));

                    String fibLabel = getRatioLabel(ratio);
                    projection.setDescription(String.format("%s from %s pivot at $%.2f",
                            fibLabel, pivot.getType().toLowerCase().replace("_", " "), pivot.getPrice()));

                    projections.add(projection);
                }
            }
        }

        return projections.stream()
                .sorted(Comparator.comparing(FibonacciTimeProjection::getDate))
                .collect(Collectors.toList());
    }

    private String getRatioLabel(double ratio) {
        // Format to 3 decimal places for clean comparison
        String formatted = String.format("%.3f", ratio);

        return switch (formatted) {
            case "0.382" -> "Fib 0.382";
            case "0.500" -> "Fib 0.500";
            case "0.618" -> "Fib 0.618";
            case "0.786" -> "Fib 0.786";
            case "1.000" -> "Fib 1.000";
            case "1.272" -> "Fib 1.272";
            case "1.618" -> "Fib 1.618";
            case "2.618" -> "Fib 2.618";
            default -> "Fib " + formatted;
        };
    }

    private double calculateFibIntensity(double ratio) {
        // Key ratios get higher intensity
        if (ratio == 0.618 || ratio == 1.618) return 0.9;  // Golden ratios
        if (ratio == 0.382 || ratio == 0.786) return 0.7;  // Important retracements
        if (ratio == 0.500) return 0.8;  // Psychological level
        if (ratio == 1.272 || ratio == 2.618) return 0.6;  // Extensions
        return 0.5;
    }

    private List<PricePivot> findRecentPivots(List<BinanceHistoricalService.OHLCData> historicalData) {
        List<PricePivot> pivots = new ArrayList<>();

        if (historicalData.size() < 5) return pivots;

        // Your existing pivot detection logic for recent swings
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
                        0.7
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
                        0.7
                ));
            }
        }

        return pivots;
    }

    private List<GannDate> calculateGannDates(List<PricePivot> pivots) {
        List<GannDate> gannDates = new ArrayList<>();

        for (PricePivot pivot : pivots) {
            // Gann anniversaries (90, 180, 360 days)
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

        // Aggregate Fibonacci projections - USE RATIOS!
        analysis.getFibonacciTimeProjections().forEach(proj -> {
            // Use the actual ratio stored in the projection
            String ratioLabel = String.format("%.3f", proj.getFibonacciRatio()); // "0.786"
            dateSignals.computeIfAbsent(proj.getDate(), k -> new ArrayList<>())
                    .add("FIB_" + ratioLabel); // "FIB_0.786" instead of "FIB_79"
        });

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
    private String determineProjectionType(String pivotType, double fibNumber) {
        return pivotType.equals("HIGH") || pivotType.equals("MAJOR_HIGH") ? "RESISTANCE" : "SUPPORT";
    }

    private LocalDate convertTimestampToDate(long timestamp) {
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate();
    }
}