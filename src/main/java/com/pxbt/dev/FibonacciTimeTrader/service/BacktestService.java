package com.pxbt.dev.FibonacciTimeTrader.service;

import com.pxbt.dev.FibonacciTimeTrader.model.ForecastDay;
import com.pxbt.dev.FibonacciTimeTrader.model.PricePivot;
import com.pxbt.dev.FibonacciTimeTrader.model.VortexAnalysis;
import com.pxbt.dev.FibonacciTimeTrader.model.VortexWindow;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BacktestService {

    private final TimeGeometryService timeGeometryService;
    private final BinanceHistoricalService binanceHistoricalService;
    private final SolarForecastService solarForecastService;

    public BacktestService(TimeGeometryService timeGeometryService,
                           BinanceHistoricalService binanceHistoricalService, SolarForecastService solarForecastService) {
        this.timeGeometryService = timeGeometryService;
        this.binanceHistoricalService = binanceHistoricalService;
        this.solarForecastService = solarForecastService;
    }

    /**
     * Test Fibonacci projections - FIXED to calculate real sample sizes
     */
    public FibonacciPerformance backtestFibonacciProjections(String symbol) {
        log.info("🔬 Backtesting Fibonacci & Harmonic projections for {}", symbol);

        // ✅ CORRECT: Load data on-demand
        List<BinanceHistoricalService.OHLCData> historicalData =
                binanceHistoricalService.getHistoricalData(symbol);

        // DEBUG: Log what we're getting
        log.info("📊 Historical data for {}: {} points", symbol,
                historicalData != null ? historicalData.size() : 0);

        if (historicalData != null && !historicalData.isEmpty()) {
            log.info("📅 Date range: {} to {}",
                    convertTimestampToDate(historicalData.get(0).timestamp()),
                    convertTimestampToDate(historicalData.get(historicalData.size()-1).timestamp()));
        }

        // ✅ FIXED: Remove the reload attempt - getHistoricalData() already handles it
        if (historicalData == null || historicalData.size() < 100) { // Reduced from 500 to 100
            log.error("❌ Insufficient data for backtest: {} points",
                    historicalData != null ? historicalData.size() : 0);
            return createEmptyFibonacciPerformance(symbol);
        }

        // ✅ REST OF YOUR CODE STAYS THE SAME...
        // Use DOUBLE for ratios, store results keyed by ratio
        Map<Double, List<Double>> fibResults = new HashMap<>();

        // Include Harmonic/Geometric ratios
        double[] fibRatios = {
                // Fibonacci ratios
                0.382, 0.500, 0.618, 0.786, 1.000, 1.272, 1.618, 2.618,
                // Harmonic/Geometric ratios
                0.333, 0.667, 1.333, 1.500, 1.667, 2.000, 2.333, 2.500, 2.667, 3.000
        };

        for (double ratio : fibRatios) {
            fibResults.put(ratio, new ArrayList<>());
        }

        // Base period in days
        int basePeriod = 100; // 100 days = 1.0 ratio

        // Test each ratio
        for (int i = 0; i < historicalData.size() - (basePeriod * 3); i++) {
            // Need room for longest projection (3.0 * 100 = 300 days)
            double priceAtStart = historicalData.get(i).close();

            for (double ratio : fibRatios) {
                // Convert ratio to days
                int projectionDays = (int) Math.round(basePeriod * ratio);
                int targetIndex = i + projectionDays;

                if (targetIndex < historicalData.size()) {
                    double priceAtFib = historicalData.get(targetIndex).close();
                    double change = calculateChange(priceAtStart, priceAtFib);
                    fibResults.get(ratio).add(change);
                }
            }
        }

        FibonacciPerformance performance = new FibonacciPerformance();
        performance.setSymbol(symbol);

        // Calculate statistics for each ratio
        Map<Double, FibStats> stats = new HashMap<>();
        for (double ratio : fibRatios) {
            List<Double> changes = fibResults.get(ratio);
            if (!changes.isEmpty()) {
                FibStats fibStats = calculateFibStats(ratio, changes);
                stats.put(ratio, fibStats);

                int days = (int) Math.round(basePeriod * ratio);
                String ratioType =
                        isRatio(ratio, 0.333) || isRatio(ratio, 0.667) ||
                                isRatio(ratio, 1.333) || isRatio(ratio, 1.667) ||
                                isRatio(ratio, 2.333) || isRatio(ratio, 2.667) ||
                                isRatio(ratio, 3.333) || isRatio(ratio, 3.667) ? "Harmonic" :
                                isRatio(ratio, 1.5) || isRatio(ratio, 2.5) || isRatio(ratio, 3.5) ? "Geometric" :
                                        isRatio(ratio, 2.0) ? "200% extension" :
                                                isRatio(ratio, 3.0) ? "300% extension" : "Fibonacci";

                log.info("{} {} ({} days): {} samples, {} success, {}% avg return",
                        ratioType, String.format("%.3f", ratio), days, changes.size(),
                        fibStats.getSuccessRate(), fibStats.getAverageChange());
            }
        }

        performance.setFibonacciStats(stats);
        return performance;
    }


    private FibStats calculateFibStats(double ratio, List<Double> changes) {
        FibStats stats = new FibStats();
        stats.setRatio(ratio); // Store the actual ratio
        stats.setSampleSize(changes.size());
        stats.setAverageChange(changes.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        stats.setMaxChange(changes.stream().mapToDouble(Double::doubleValue).max().orElse(0));
        stats.setMinChange(changes.stream().mapToDouble(Double::doubleValue).min().orElse(0));

        // Calculate standard deviation
        double mean = stats.getAverageChange();
        double variance = changes.stream()
                .mapToDouble(c -> Math.pow(c - mean, 2))
                .average().orElse(0);
        stats.setStdDev(Math.sqrt(variance));

        // Success rate (positive changes)
        double successRate = changes.stream()
                .filter(c -> c > 0)
                .count() * 100.0 / changes.size();
        stats.setSuccessRate(successRate);

        return stats;
    }

    /**
     * Test Gann anniversary dates (90, 180, 360 days) - CORRECTED VERSION
     */
    public GannPerformance backtestGannAnniversaries(String symbol) {
        log.info("🔬 BACKTESTING ALL GANN CYCLES for {}", symbol);

        List<BinanceHistoricalService.OHLCData> historicalData =
                binanceHistoricalService.getHistoricalData(symbol);

        if (historicalData == null || historicalData.size() < 400) {
            log.warn("Insufficient data for Gann backtest: {} points",
                    historicalData != null ? historicalData.size() : 0);
            return createEmptyGannPerformance(symbol);
        }

        // ✅ UPDATED: Test ALL Gann cycles
        int[] gannPeriods = {30, 45, 60, 90, 120, 135, 144, 180, 225, 270, 315, 360, 540, 720};

        // 1. Find ACTUAL pivot points from TimeGeometryService logic
        List<PricePivot> majorPivots = extractMajorPivotsFromService(symbol);

        // If no major pivots from service, find them from historical data
        if (majorPivots.isEmpty()) {
            log.info("No major pivots from service, finding from historical data...");

            // Step 1: Find pivot DATES (returns List<LocalDate>)
            List<LocalDate> pivotDates = findSignificantPivots(historicalData, 20);

            // Step 2: Convert dates to PricePivot objects (returns List<PricePivot>)
            majorPivots = convertToPricePivots(historicalData, pivotDates);

            log.info("Found {} pivot dates, converted to {} price pivots",
                    pivotDates.size(), majorPivots.size());
        }

        log.info("Found {} significant pivot points since {}",
                majorPivots.size(),
                majorPivots.isEmpty() ? "N/A" : majorPivots.get(0).getDate());

        // 2. Test each Gann period
        Map<Integer, List<Double>> results = new HashMap<>();
        Map<Integer, Integer> sampleSizes = new HashMap<>();

        for (int period : gannPeriods) {
            results.put(period, new ArrayList<>());
            sampleSizes.put(period, 0);
        }

        for (PricePivot pivot : majorPivots) {
            int pivotIndex = findDateIndex(historicalData, pivot.getDate());

            if (pivotIndex != -1) {
                for (int period : gannPeriods) {
                    int anniversaryIndex = pivotIndex + period;

                    if (anniversaryIndex < historicalData.size()) {
                        double startPrice = historicalData.get(pivotIndex).close();
                        double endPrice = historicalData.get(anniversaryIndex).close();
                        double returnPct = calculateChange(startPrice, endPrice);

                        results.get(period).add(returnPct);
                        sampleSizes.put(period, sampleSizes.get(period) + 1);
                    }
                }
            }
        }

        // 3. Calculate statistics
        Map<Integer, Double> avgReturns = new HashMap<>();
        Map<Integer, Double> successRates = new HashMap<>();
        Map<Integer, Double> avgPositiveReturns = new HashMap<>();
        Map<Integer, Double> avgNegativeReturns = new HashMap<>();

        for (int period : gannPeriods) {
            List<Double> changes = results.get(period);

            if (!changes.isEmpty() && changes.size() >= 5) { // Minimum 5 samples
                // Average return
                double avgReturn = changes.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);
                avgReturns.put(period, avgReturn);

                // Success rate (% of positive returns)
                double successRate = changes.stream()
                        .filter(c -> c > 0)
                        .count() * 100.0 / changes.size();
                successRates.put(period, successRate);

                // Average positive returns only
                double avgPositive = changes.stream()
                        .filter(c -> c > 0)
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);
                avgPositiveReturns.put(period, avgPositive);

                // Average negative returns only
                double avgNegative = changes.stream()
                        .filter(c -> c < 0)
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);
                avgNegativeReturns.put(period, avgNegative);

                log.info("Gann {} days: {} samples, {}% success, {}% avg return",
                        period, changes.size(), successRate, avgReturn);
            }
        }

        // 4. Rank cycles by performance
        List<Map.Entry<Integer, Double>> rankedBySuccess = successRates.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(5)
                .toList();

        log.info("🏆 TOP 5 GANN CYCLES BY SUCCESS RATE for {}:", symbol);
        for (int i = 0; i < rankedBySuccess.size(); i++) {
            Map.Entry<Integer, Double> entry = rankedBySuccess.get(i);
            log.info("   {}. {} days: {}% success ({} samples)",
                    i + 1, entry.getKey(), entry.getValue(),
                    sampleSizes.get(entry.getKey()));
        }

        // 5. Build response
        GannPerformance performance = new GannPerformance();
        performance.setSymbol(symbol);
        performance.setSampleSizes(sampleSizes);
        performance.setAverageReturns(avgReturns);
        performance.setSuccessRates(successRates);
        performance.setTimestamp(System.currentTimeMillis());

        return performance;
    }

    /**
     * Extract major pivots using the SAME logic as TimeGeometryService
     */
    private List<PricePivot> extractMajorPivotsFromService(String symbol) {
        try {
            // Get monthly data
            List<BinanceHistoricalService.OHLCData> monthlyData =
                    binanceHistoricalService.getMonthlyData(symbol);

            if (monthlyData == null || monthlyData.isEmpty()) {
                return new ArrayList<>();
            }

            // Use the SAME logic as TimeGeometryService.getMajorCyclePivots()
            List<PricePivot> pivots = new ArrayList<>();

            // BTC specific
            if (symbol.equals("BTC")) {
//                pivots.add(new PricePivot(LocalDate.of(2018, 12, 15), 3100.0, "MAJOR_LOW", 1.0));
                pivots.add(new PricePivot(LocalDate.of(2023, 1, 1), 15455.0, "MAJOR_LOW", 0.9));
//                pivots.add(new PricePivot(LocalDate.of(2024, 3, 1), 72000.0, "MAJOR_HIGH", 0.8));
                pivots.add(new PricePivot(LocalDate.of(2025, 10, 1), 126272.76, "MAJOR_HIGH", 1.0));
                return pivots;
            }

            // For other symbols (we'll fix this next)
            // Fallback: find highest and lowest
            BinanceHistoricalService.OHLCData lowest = monthlyData.stream()
                    .min(Comparator.comparingDouble(BinanceHistoricalService.OHLCData::low))
                    .orElse(null);
            BinanceHistoricalService.OHLCData highest = monthlyData.stream()
                    .max(Comparator.comparingDouble(BinanceHistoricalService.OHLCData::high))
                    .orElse(null);

            if (lowest != null) {
                pivots.add(new PricePivot(
                        convertTimestampToDate(lowest.timestamp()),
                        lowest.low(),
                        "MAJOR_LOW",
                        1.0
                ));
            }

            if (highest != null) {
                pivots.add(new PricePivot(
                        convertTimestampToDate(highest.timestamp()),
                        highest.high(),
                        "MAJOR_HIGH",
                        1.0
                ));
            }

            return pivots;

        } catch (Exception e) {
            log.error("Failed to extract major pivots for {}: {}", symbol, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Convert pivot dates to PricePivot objects
     */
    private List<PricePivot> convertToPricePivots(
            List<BinanceHistoricalService.OHLCData> historicalData,
            List<LocalDate> pivotDates) {

        List<PricePivot> pricePivots = new ArrayList<>();

        for (LocalDate pivotDate : pivotDates) {
            // Find the data point for this date
            for (BinanceHistoricalService.OHLCData data : historicalData) {
                LocalDate dataDate = Instant.ofEpochMilli(data.timestamp())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();

                if (dataDate.equals(pivotDate)) {
                    // Determine if it was a high or low pivot
                    // Simple check: compare with neighbors
                    int index = historicalData.indexOf(data);
                    if (index > 0 && index < historicalData.size() - 1) {
                        boolean isHigh = data.high() > historicalData.get(index - 1).high() &&
                                data.high() > historicalData.get(index + 1).high();
                        boolean isLow = data.low() < historicalData.get(index - 1).low() &&
                                data.low() < historicalData.get(index + 1).low();

                        if (isHigh) {
                            pricePivots.add(new PricePivot(
                                    pivotDate,
                                    data.high(),
                                    "HIGH",
                                    0.8
                            ));
                        } else if (isLow) {
                            pricePivots.add(new PricePivot(
                                    pivotDate,
                                    data.low(),
                                    "LOW",
                                    0.8
                            ));
                        }
                    }
                    break;
                }
            }
        }

        return pricePivots;
    }

    /**
     * Helper to convert timestamp to LocalDate
     */
    private LocalDate convertTimestampToDate(long timestamp) {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    public List<FibHitResult> testHistoricalFibonacciHits(String symbol, double marginPercent, int daysTolerance) {
        log.info("🎯 Testing historical Fibonacci hits for {} ({}% margin, ±{} days)",
                symbol, marginPercent, daysTolerance);

        List<FibHitResult> results = new ArrayList<>();

        try {
            // Get historical data
            List<BinanceHistoricalService.OHLCData> historicalData =
                    binanceHistoricalService.getHistoricalData(symbol);

            if (historicalData == null || historicalData.size() < 200) {
                log.warn("Insufficient data for hit testing: {} points",
                        historicalData != null ? historicalData.size() : 0);
                return results;
            }

            // Find pivot points
            List<PricePivot> historicalPivots = findHistoricalPivots(historicalData, 30);

            // Key Fibonacci ratios to test
            double[] keyRatios = {0.236, 0.382, 0.5, 0.618, 0.786, 1.0, 1.272, 1.618, 2.0, 2.618, 3.0};

            for (PricePivot pivot : historicalPivots) {
                // Only test pivots that are old enough to have subsequent data
                if (isPivotOldEnough(pivot, 30)) {
                    for (double ratio : keyRatios) {
                        FibHitResult hit = testSingleFibProjection(pivot, ratio, historicalData, marginPercent, daysTolerance);
                        if (hit != null) {
                            results.add(hit);
                        }
                    }
                }
            }

            log.info("✅ Found {} Fibonacci hits for {}", results.size(), symbol);

        } catch (Exception e) {
            log.error("❌ Error testing Fibonacci hits: {}", e.getMessage(), e);
        }

        return results;
    }

    private boolean isPivotOldEnough(PricePivot pivot, int minDaysOld) {
        if (pivot == null || pivot.getDate() == null) return false;
        long daysOld = ChronoUnit.DAYS.between(pivot.getDate(), LocalDate.now());
        return daysOld >= minDaysOld;
    }

    // Gann hit testing method
    public List<GannHitResult> testHistoricalGannHits(String symbol, double marginPercent, int daysTolerance) {
        log.info("🎯 Testing historical Gann hits for {} ({}% margin, ±{} days)",
                symbol, marginPercent, daysTolerance);

        List<GannHitResult> results = new ArrayList<>();

        try {
            // Get historical data
            List<BinanceHistoricalService.OHLCData> historicalData =
                    binanceHistoricalService.getHistoricalData(symbol);

            if (historicalData == null || historicalData.size() < 200) {
                log.warn("Insufficient data for Gann hit testing");
                return results;
            }

            // Get major pivots from TimeGeometryService
            List<PricePivot> majorPivots = timeGeometryService.getMajorCyclePivots(symbol,
                    binanceHistoricalService.getMonthlyData(symbol));

            if (majorPivots.isEmpty()) {
                log.info("No major pivots found for Gann testing");
                return results;
            }

            // Key Gann periods to test (from STANDARD_GANN_PERIODS)
            int[] gannPeriods = {30, 45, 60, 90, 120, 135, 144, 180, 225, 270, 315, 360, 540, 720};

            for (PricePivot pivot : majorPivots) {
                // Only test pivots that are old enough
                if (isPivotOldEnough(pivot, 60)) {
                    for (int period : gannPeriods) {
                        GannHitResult hit = testSingleGannAnniversary(pivot, period, historicalData, marginPercent, daysTolerance);
                        if (hit != null) {
                            results.add(hit);
                        }
                    }
                }
            }

            log.info("✅ Found {} Gann hits for {}", results.size(), symbol);

        } catch (Exception e) {
            log.error("❌ Error testing Gann hits: {}", e.getMessage(), e);
        }

        return results;
    }

    private GannHitResult testSingleGannAnniversary(PricePivot pivot, int period,
                                                    List<BinanceHistoricalService.OHLCData> data,
                                                    double margin, int tolerance) {
        try {
            // Calculate Gann anniversary date
            LocalDate gannDate = pivot.getDate().plusDays(period);

            // Skip future dates and very old dates
            if (gannDate.isAfter(LocalDate.now().minusDays(7)) ||
                    gannDate.isBefore(LocalDate.now().minusYears(3))) {
                return null;
            }

            // Find the Gann date in data
            int gannIndex = findDateIndex(data, gannDate);
            if (gannIndex == -1 || gannIndex >= data.size() - tolerance) {
                return null;
            }

            double priceAtGann = data.get(gannIndex).close();

            // Check for significant move within tolerance window
            boolean hitFound = false;
            double maxMove = 0;
            LocalDate actualMoveDate = gannDate;
            String direction = "NONE";
            int daysOffset = 0;

            for (int offset = -tolerance; offset <= tolerance; offset++) {
                int checkIndex = gannIndex + offset;
                if (checkIndex >= 0 && checkIndex < data.size() && checkIndex != gannIndex) {
                    double checkPrice = data.get(checkIndex).close();
                    double movePercent = ((checkPrice - priceAtGann) / priceAtGann) * 100;

                    if (Math.abs(movePercent) > Math.abs(maxMove)) {
                        maxMove = movePercent;
                        actualMoveDate = convertTimestampToDate(data.get(checkIndex).timestamp());
                        direction = movePercent > 0 ? "UP" : "DOWN";
                        daysOffset = offset;
                    }

                    // Check if this move exceeds our margin threshold
                    if (Math.abs(movePercent) >= margin) {
                        hitFound = true;
                    }
                }
            }

            if (Math.abs(maxMove) >= margin * 0.5) {
                GannHitResult result = new GannHitResult();
                result.setPivotDate(pivot.getDate());
                result.setPivotPrice(pivot.getPrice());
                result.setPivotType(pivot.getType());
                result.setGannPeriod(period);
                result.setGannDate(gannDate);
                result.setActualMoveDate(actualMoveDate);
                result.setMovePercent(maxMove);
                result.setDirection(direction);
                result.setHit(hitFound);
                result.setDaysFromProjection(daysOffset);

                // Determine if this was a reversal (opposite of pivot type)
                boolean isReversal = (pivot.getType().contains("HIGH") && direction.equals("DOWN")) ||
                        (pivot.getType().contains("LOW") && direction.equals("UP"));
                result.setReversal(isReversal);

                return result;
            }

        } catch (Exception e) {
            log.warn("Error testing Gann anniversary: {}", e.getMessage());
        }

        return null;
    }

    @Data
    public static class GannHitResult {
        private LocalDate pivotDate;
        private double pivotPrice;
        private String pivotType;
        private int gannPeriod;
        private LocalDate gannDate;
        private LocalDate actualMoveDate;
        private double movePercent;
        private String direction;
        private boolean hit;
        private boolean reversal;
        private int daysFromProjection;

        public String getFormattedMove() {
            return String.format("%s %.2f%%", direction, Math.abs(movePercent));
        }

        public String getGannLabel() {
            return gannPeriod + "D";
        }
    }


    private FibHitResult testSingleFibProjection(PricePivot pivot, double ratio,
                                                 List<BinanceHistoricalService.OHLCData> data,
                                                 double margin, int tolerance) {
        try {
            // Calculate projection date (using 100-day base)
            int projectedDays = (int) Math.round(100 * ratio);
            LocalDate projectedDate = pivot.getDate().plusDays(projectedDays);

            // Skip future dates and very old dates
            if (projectedDate.isAfter(LocalDate.now().minusDays(7)) ||
                    projectedDate.isBefore(LocalDate.now().minusYears(2))) {
                return null;
            }

            // Find the projected date in data
            int projIndex = findDateIndex(data, projectedDate);
            if (projIndex == -1 || projIndex >= data.size() - tolerance) {
                return null;
            }

            double priceAtProjection = data.get(projIndex).close();

            // Check for significant move within tolerance window
            boolean hitFound = false;
            double maxMove = 0;
            LocalDate actualMoveDate = projectedDate;
            String direction = "NONE";
            int daysOffset = 0;

            for (int offset = -tolerance; offset <= tolerance; offset++) {
                int checkIndex = projIndex + offset;
                if (checkIndex >= 0 && checkIndex < data.size() && checkIndex != projIndex) {
                    double checkPrice = data.get(checkIndex).close();
                    double movePercent = ((checkPrice - priceAtProjection) / priceAtProjection) * 100;

                    if (Math.abs(movePercent) > Math.abs(maxMove)) {
                        maxMove = movePercent;
                        actualMoveDate = convertTimestampToDate(data.get(checkIndex).timestamp());
                        direction = movePercent > 0 ? "UP" : "DOWN";
                        daysOffset = offset;
                    }

                    // Check if this move exceeds our margin threshold
                    if (Math.abs(movePercent) >= margin) {
                        hitFound = true;
                    }
                }
            }

            if (Math.abs(maxMove) >= margin * 0.5) { // Lower threshold for inclusion
                FibHitResult result = new FibHitResult();
                result.setPivotDate(pivot.getDate());
                result.setPivotPrice(pivot.getPrice());
                result.setPivotType(pivot.getType());
                result.setFibonacciRatio(ratio);
                result.setProjectedDate(projectedDate);
                result.setActualMoveDate(actualMoveDate);
                result.setMovePercent(maxMove);
                result.setDirection(direction);
                result.setHit(hitFound);
                result.setDaysFromProjection(daysOffset);
                return result;
            }

        } catch (Exception e) {
            log.warn("Error testing Fib projection: {}", e.getMessage());
        }

        return null;
    }
    // ===== HELPER METHODS =====

    /**
     * Find significant pivot points in price data
     */
    private List<LocalDate> findSignificantPivots(
            List<BinanceHistoricalService.OHLCData> historicalData,
            int lookbackDays) {

        List<LocalDate> pivots = new ArrayList<>();

        for (int i = lookbackDays; i < historicalData.size() - lookbackDays; i++) {
            boolean isHigh = true;
            boolean isLow = true;
            double currentHigh = historicalData.get(i).high();
            double currentLow = historicalData.get(i).low();

            // Check surrounding window
            for (int j = i - lookbackDays; j <= i + lookbackDays; j++) {
                if (j == i) continue;

                if (historicalData.get(j).high() > currentHigh) {
                    isHigh = false;
                }
                if (historicalData.get(j).low() < currentLow) {
                    isLow = false;
                }

                // Early exit if both false
                if (!isHigh && !isLow) break;
            }

            if (isHigh || isLow) {
                LocalDate pivotDate = Instant.ofEpochMilli(historicalData.get(i).timestamp())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
                pivots.add(pivotDate);
            }
        }

        log.info("Found {} significant pivot points", pivots.size());
        return pivots;
    }

    /**
     * Find index of a specific date in historical data
     */
    private int find1DateIndex(
            List<BinanceHistoricalService.OHLCData> data,
            LocalDate targetDate) {

        for (int i = 0; i < data.size(); i++) {
            LocalDate currentDate = Instant.ofEpochMilli(data.get(i).timestamp())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();

            if (currentDate.equals(targetDate)) {
                return i;
            }
        }
        return -1;
    }

    private double calculateChange(double startPrice, double endPrice) {
        return ((endPrice - startPrice) / startPrice) * 100;
    }

    private FibonacciPerformance createEmptyFibonacciPerformance(String symbol) {
        FibonacciPerformance performance = new FibonacciPerformance();
        performance.setSymbol(symbol);
        performance.setFibonacciStats(new HashMap<>());
        return performance;
    }

    private GannPerformance createEmptyGannPerformance(String symbol) {
        GannPerformance performance = new GannPerformance();
        performance.setSymbol(symbol);
        performance.setSampleSizes(new HashMap<>());
        performance.setAverageReturns(new HashMap<>());
        performance.setSuccessRates(new HashMap<>());
        performance.setTimestamp(System.currentTimeMillis());
        return performance;
    }

    // ===== DATA MODELS =====

   @Data
    public static class SignalOutcome {
        private LocalDate signalDate;
        private String signalType;
        private double priceAtSignal;
        private double priceChange7Days;
        private double priceChange14Days;
        private double priceChange30Days;
        private boolean wasSignificant;
    }

    @Data
    public static class SignalPerformance {
        private String signalType;
        private int totalSignals;
        private double averageReturn7Days;
        private double averageReturn14Days;
        private double averageReturn30Days;
        private double successRate7Days;
        private double successRate14Days;
        private double successRate30Days;
    }

    @Data
    public static class FibonacciPerformance {
        private String symbol;
        private Map<Double, FibStats> fibonacciStats; // Keyed by ratio, not integer
    }

    @Data
    public static class FibStats {
        private double ratio;           // The Fibonacci ratio (0.382, 0.618, etc.)
        private int sampleSize;
        private double averageChange;
        private double maxChange;
        private double minChange;
        private double stdDev;
        private double successRate; // Percentage of positive changes

        // Helper to get days (assuming 100-day base)
        public int getDays() {
            return (int) Math.round(100 * ratio);
        }
    }

    @Data
    public static class GannPerformance {
        private String symbol;
        private Map<Integer, Double> averageReturns; // Period -> avg % return
        private Map<Integer, Double> successRates;   // Period -> % success
        private Map<Integer, Integer> sampleSizes;   // Period -> number of samples
        private Long timestamp;
    }

    @Data
    public static class VortexPerformance {
        private String symbol;
        private String message;
    }

    private boolean isRatio(double value, double target) {
        return Math.abs(value - target) < 0.001;
    }



    /**
     * Enhanced: Backtest Confluence Windows (formerly Vortex Windows)
     * Tests actual market behavior on historical confluence dates
     */
    public ConfluencePerformance backtestConfluenceWindows(String symbol) {
        log.info("🔬 Backtesting Confluence Windows for {}", symbol);

        ConfluencePerformance performance = new ConfluencePerformance();
        performance.setSymbol(symbol);

        try {
            // 1. Get historical data
            List<BinanceHistoricalService.OHLCData> historicalData =
                    binanceHistoricalService.getHistoricalData(symbol);

            if (historicalData == null || historicalData.size() < 100) {
                log.warn("Insufficient data for confluence backtest: {} points",
                        historicalData != null ? historicalData.size() : 0);
                return performance;
            }

            // 2. Get actual vortex windows from TimeGeometryService
            VortexAnalysis analysis = timeGeometryService.analyzeSymbol(symbol);
            List<VortexWindow> vortexWindows = analysis.getVortexWindows();

            if (vortexWindows == null || vortexWindows.isEmpty()) {
                log.info("No vortex windows found for {}", symbol);
                return performance;
            }

            // 3. Test each vortex window historically
            Map<Integer, List<Double>> returnsByTimeframe = new HashMap<>();
            Map<Integer, List<Boolean>> successesByTimeframe = new HashMap<>();

            int[] timeframes = {1, 3, 7, 14, 30};
            for (int days : timeframes) {
                returnsByTimeframe.put(days, new ArrayList<>());
                successesByTimeframe.put(days, new ArrayList<>());
            }

            int totalWindows = 0;

            for (VortexWindow window : vortexWindows) {
                LocalDate windowDate = window.getDate();

                // Skip future windows (only test past ones)
                if (windowDate.isAfter(LocalDate.now())) {
                    continue;
                }

                // Find this date in historical data
                int windowIndex = -1;
                for (int i = 0; i < historicalData.size(); i++) {
                    LocalDate dataDate = convertTimestampToDate(historicalData.get(i).timestamp());
                    if (dataDate.equals(windowDate)) {
                        windowIndex = i;
                        break;
                    }
                }

                if (windowIndex == -1 || windowIndex >= historicalData.size() - 30) {
                    continue; // Can't test this window
                }

                double priceAtWindow = historicalData.get(windowIndex).close();
                totalWindows++;

                // Test each timeframe
                for (int days : timeframes) {
                    int targetIndex = windowIndex + days;
                    if (targetIndex < historicalData.size()) {
                        double futurePrice = historicalData.get(targetIndex).close();
                        double returnPct = ((futurePrice - priceAtWindow) / priceAtWindow) * 100;

                        returnsByTimeframe.get(days).add(returnPct);
                        successesByTimeframe.get(days).add(returnPct > 0);
                    }
                }
            }

            // 4. Calculate performance metrics
            Map<Integer, Double> avgReturns = new HashMap<>();
            Map<Integer, Double> successRates = new HashMap<>();
            Map<Integer, Integer> sampleSizes = new HashMap<>();

            for (int days : timeframes) {
                List<Double> returns = returnsByTimeframe.get(days);
                List<Boolean> successes = successesByTimeframe.get(days);

                if (!returns.isEmpty()) {
                    // Average return
                    double avgReturn = returns.stream()
                            .mapToDouble(Double::doubleValue)
                            .average()
                            .orElse(0.0);
                    avgReturns.put(days, avgReturn);

                    // Success rate
                    long successCount = successes.stream()
                            .filter(Boolean::booleanValue)
                            .count();
                    double successRate = (successCount * 100.0) / successes.size();
                    successRates.put(days, successRate);

                    // Sample size
                    sampleSizes.put(days, returns.size());
                }
            }

            // 5. Set performance data
            performance.setTotalWindows(totalWindows);
            performance.setAverageReturns(avgReturns);
            performance.setSuccessRates(successRates);
            performance.setSampleSizes(sampleSizes);

            // Calculate overall success rate (using 7-day as benchmark)
            if (successRates.containsKey(7)) {
                performance.setOverallSuccessRate(successRates.get(7));
            }

            log.info("✅ Confluence backtest complete for {}: {} windows tested",
                    symbol, totalWindows);

        } catch (Exception e) {
            log.error("❌ Error in confluence backtest for {}: {}", symbol, e.getMessage(), e);
        }

        return performance;
    }

    /**
     * Simulate confluence detection for historical periods
     */
    private List<HistoricalConfluenceWindow> simulateHistoricalConfluenceDetection(
            String symbol,
            List<BinanceHistoricalService.OHLCData> historicalData) {

        List<HistoricalConfluenceWindow> windows = new ArrayList<>();

        // We'll simulate detection for each day in history (except last 30 days)
        for (int i = 30; i < historicalData.size() - 30; i += 7) { // Weekly checks
            try {
                LocalDate currentDate = convertTimestampToDate(
                        historicalData.get(i).timestamp());

                // Check if this date would have been detected as confluence
                HistoricalConfluenceWindow window =
                        checkForHistoricalConfluence(symbol, currentDate, historicalData, i);

                if (window != null) {
                    windows.add(window);
                }

            } catch (Exception e) {
                log.warn("Failed to check confluence at index {}: {}", i, e.getMessage());
            }
        }

        return windows;
    }

    /**
     * Check if a specific historical date had confluence signals
     */
    private HistoricalConfluenceWindow checkForHistoricalConfluence(
            String symbol,
            LocalDate date,
            List<BinanceHistoricalService.OHLCData> historicalData,
            int dataIndex) {

        // Get data up to this date (looking back)
        List<BinanceHistoricalService.OHLCData> dataUpToDate =
                historicalData.subList(0, dataIndex + 1);

        // Find major pivots up to this date
        List<PricePivot> pivotsUpToDate = findMajorPivotsInData(dataUpToDate);

        // Check for Fibonacci projections from recent pivots
        List<FibonacciSignal> fibSignals = checkFibonacciSignals(date, pivotsUpToDate);

        // Check for Gann anniversaries
        List<GannSignal> gannSignals = checkGannSignals(date, pivotsUpToDate);

        // Check for solar activity (simulated - you'd need historical solar data)
        List<SolarSignal> solarSignals = checkSolarSignals(date);

        // Determine if this is a confluence window
        int totalSignals = fibSignals.size() + gannSignals.size() + solarSignals.size();

        if (totalSignals >= 2) { // At least 2 signals for confluence
            HistoricalConfluenceWindow window = new HistoricalConfluenceWindow();
            window.setDate(date);
            window.setFibSignals(fibSignals);
            window.setGannSignals(gannSignals);
            window.setSolarSignals(solarSignals);
            window.setTotalSignals(totalSignals);

            // Get price at signal date
            double priceAtSignal = historicalData.get(dataIndex).close();
            window.setPriceAtSignal(priceAtSignal);

            // Calculate future outcomes (next 1, 7, 30 days)
            calculateFutureOutcomes(window, dataIndex, historicalData);

            return window;
        }

        return null;
    }

    /**
     * Check Fibonacci signals for a specific date
     */
    private List<FibonacciSignal> checkFibonacciSignals(
            LocalDate date,
            List<PricePivot> pivots) {

        List<FibonacciSignal> signals = new ArrayList<>();

        for (PricePivot pivot : pivots) {
            // Only consider pivots from last 2 years
            if (pivot.getDate().isAfter(date.minusYears(2))) {

                // Check Fibonacci time ratios (days between pivot and date)
                long daysBetween = ChronoUnit.DAYS.between(pivot.getDate(), date);
                double ratio = daysBetween / 100.0; // Based on 100-day base

                // Key Fibonacci ratios
                double[] keyRatios = {0.236, 0.382, 0.500, 0.618, 0.786, 1.000,
                        1.272, 1.618, 2.618, 0.333, 0.667, 1.333,
                        1.500, 1.667, 2.000, 2.333, 2.500, 2.667, 3.000};

                for (double keyRatio : keyRatios) {
                    if (Math.abs(ratio - keyRatio) < 0.02) { // 2% tolerance
                        FibonacciSignal signal = new FibonacciSignal();
                        signal.setRatio(keyRatio);
                        signal.setDays((int) daysBetween);
                        signal.setSourcePivot(pivot);
                        signal.setAccuracy(Math.abs(ratio - keyRatio));
                        signals.add(signal);
                    }
                }
            }
        }

        return signals;
    }

    /**
     * Check Gann signals for a specific date
     */
    private List<GannSignal> checkGannSignals(LocalDate date, List<PricePivot> pivots) {
        List<GannSignal> signals = new ArrayList<>();

        for (PricePivot pivot : pivots) {
            // Only consider pivots from last 3 years
            if (pivot.getDate().isAfter(date.minusYears(3))) {

                long daysBetween = ChronoUnit.DAYS.between(pivot.getDate(), date);

                // Key Gann periods
                int[] gannPeriods = {30, 45, 60, 72, 90, 120, 135, 144, 150,
                        180, 216, 225, 240, 270, 288, 300, 315,
                        330, 360, 540, 720};

                for (int period : gannPeriods) {
                    if (Math.abs(daysBetween - period) <= 2) { // ±2 days tolerance
                        GannSignal signal = new GannSignal();
                        signal.setPeriod(period);
                        signal.setDays((int) daysBetween);
                        signal.setSourcePivot(pivot);
                        signal.setAccuracy(Math.abs(daysBetween - period));
                        signals.add(signal);
                    }
                }
            }
        }

        return signals;
    }

    /**
     * Check solar signals (simulated - need historical AP data)
     */
    private List<SolarSignal> checkSolarSignals(LocalDate date) {
        List<SolarSignal> signals = new ArrayList<>();

        // We would query historical solar data
        // We can integrate with historical solar databases here
        // For now can return empty

        return signals;
    }

    /**
     * Calculate what happened after each confluence window
     */
    private void calculateFutureOutcomes(
            HistoricalConfluenceWindow window,
            int signalIndex,
            List<BinanceHistoricalService.OHLCData> historicalData) {

        double priceAtSignal = window.getPriceAtSignal();

        // Check price at various future intervals
        for (int days : new int[]{1, 3, 7, 14, 30}) {
            int futureIndex = signalIndex + days;

            if (futureIndex < historicalData.size()) {
                double futurePrice = historicalData.get(futureIndex).close();
                double returnPct = ((futurePrice - priceAtSignal) / priceAtSignal) * 100;

                switch(days) {
                    case 1: window.setReturn1Day(returnPct); break;
                    case 3: window.setReturn3Days(returnPct); break;
                    case 7: window.setReturn7Days(returnPct); break;
                    case 14: window.setReturn14Days(returnPct); break;
                    case 30: window.setReturn30Days(returnPct); break;
                }

                // Determine if reversal occurred (price moved opposite to trend)
                window.setReversalOccurred(checkForReversal(
                        signalIndex, futureIndex, historicalData));
            }
        }

        // Calculate volatility after signal
        window.setPostSignalVolatility(calculateVolatility(
                signalIndex, Math.min(signalIndex + 20, historicalData.size()),
                historicalData));
    }

    /**
     * Analyze all confluence outcomes
     */
    private ConfluencePerformance analyzeConfluenceOutcomes(
            String symbol,
            List<HistoricalConfluenceWindow> windows,
            List<BinanceHistoricalService.OHLCData> historicalData) {

        ConfluencePerformance performance = new ConfluencePerformance();
        performance.setSymbol(symbol);
        performance.setTotalWindows(windows.size());
        performance.setTimestamp(System.currentTimeMillis());

        if (windows.isEmpty()) {
            return performance;
        }

        // Calculate average returns
        Map<Integer, Double> avgReturns = new HashMap<>();
        Map<Integer, Double> successRates = new HashMap<>();
        Map<Integer, Integer> sampleSizes = new HashMap<>();

        int[] timeframes = {1, 3, 7, 14, 30};

        for (int days : timeframes) {
            List<Double> returns = new ArrayList<>();
            int positiveCount = 0;

            for (HistoricalConfluenceWindow window : windows) {
                Double returnValue = getReturnForDays(window, days);
                if (returnValue != null) {
                    returns.add(returnValue);
                    if (returnValue > 0) positiveCount++;
                }
            }

            if (!returns.isEmpty()) {
                double avgReturn = returns.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);

                double successRate = (positiveCount * 100.0) / returns.size();

                avgReturns.put(days, avgReturn);
                successRates.put(days, successRate);
                sampleSizes.put(days, returns.size());
            }
        }

        performance.setAverageReturns(avgReturns);
        performance.setSuccessRates(successRates);
        performance.setSampleSizes(sampleSizes);

        // Analyze by signal type
        Map<String, SignalTypePerformance> byType = analyzeBySignalType(windows);
        performance.setPerformanceBySignalType(byType);

        // Calculate overall statistics
        performance.setOverallSuccessRate(calculateOverallSuccessRate(windows));
        performance.setAverageReturnAllWindows(calculateAverageReturn(windows));
        performance.setBestWindow(findBestWindow(windows));
        performance.setWorstWindow(findWorstWindow(windows));

        // Signal effectiveness metrics
        performance.setSignalEffectiveness(calculateEffectivenessMetrics(windows));

        return performance;
    }

    /**
     * Test Solar Impact on Markets
     */
    public SolarImpactAnalysis analyzeSolarImpact(String symbol) {
        log.info("☀️ Analyzing solar impact on {}", symbol);

        SolarImpactAnalysis analysis = new SolarImpactAnalysis();
        analysis.setSymbol(symbol);

        try {
            // 1. Get solar forecast data
            List<ForecastDay> solarDays = solarForecastService.getHighApDates();

            // 2. Get historical data
            List<BinanceHistoricalService.OHLCData> historicalData =
                    binanceHistoricalService.getHistoricalData(symbol);

            if (solarDays.isEmpty() || historicalData == null || historicalData.size() < 100) {
                log.warn("Insufficient data for solar impact analysis");
                return analysis;
            }

            // 3. Find high AP days in historical data
            List<Double> highApReturns = new ArrayList<>();
            List<Double> normalReturns = new ArrayList<>();

            // Track volatility
            List<Double> highApVolatility = new ArrayList<>();
            List<Double> normalVolatility = new ArrayList<>();

            for (int i = 1; i < historicalData.size(); i++) {
                LocalDate currentDate = convertTimestampToDate(historicalData.get(i).timestamp());
                LocalDate prevDate = convertTimestampToDate(historicalData.get(i-1).timestamp());

                double dailyReturn = ((historicalData.get(i).close() - historicalData.get(i-1).close())
                        / historicalData.get(i-1).close()) * 100;
                double dailyRange = (historicalData.get(i).high() - historicalData.get(i).low())
                        / historicalData.get(i).close();

                // Check if this was a high AP day
                boolean isHighApDay = solarDays.stream()
                        .anyMatch(day -> day.getDate().equals(currentDate) && day.getAp() >= 12);

                if (isHighApDay) {
                    highApReturns.add(dailyReturn);
                    highApVolatility.add(dailyRange);
                } else {
                    normalReturns.add(dailyReturn);
                    normalVolatility.add(dailyRange);
                }
            }

            // 4. Calculate statistics
            analysis.setHighApDays(highApReturns.size());

            if (!highApReturns.isEmpty()) {
                double avgHighApReturn = highApReturns.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);
                analysis.setAverageReturnHighAp(avgHighApReturn);

                double avgHighApVol = highApVolatility.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);

                if (!normalReturns.isEmpty()) {
                    double avgNormalReturn = normalReturns.stream()
                            .mapToDouble(Double::doubleValue)
                            .average()
                            .orElse(0.0);
                    analysis.setAverageReturnNormal(avgNormalReturn);

                    double avgNormalVol = normalVolatility.stream()
                            .mapToDouble(Double::doubleValue)
                            .average()
                            .orElse(0.0);

                    // Calculate volatility ratio
                    if (avgNormalVol > 0) {
                        analysis.setVolatilityRatio(avgHighApVol / avgNormalVol);
                    }

                    // Simple correlation
                    double meanHighAp = avgHighApReturn;
                    double meanNormal = avgNormalReturn;

                    if (Math.abs(meanHighAp - meanNormal) > 0.5) {
                        analysis.setImpactAssessment(meanHighAp > meanNormal ? "Positive" : "Negative");
                    } else {
                        analysis.setImpactAssessment("Neutral");
                    }
                }
            }

            log.info("✅ Solar impact analysis complete: {} high AP days found",
                    highApReturns.size());

        } catch (Exception e) {
            log.error("❌ Error in solar impact analysis: {}", e.getMessage(), e);
        }

        return analysis;
    }

    /**
     * Comprehensive Time Geometry Analysis
     */
    public TimeGeometryPerformance comprehensiveAnalysis(String symbol) {
        log.info("🎯 Comprehensive time geometry analysis for {}", symbol);

        TimeGeometryPerformance performance = new TimeGeometryPerformance();
        performance.setSymbol(symbol);

        // Run all backtests
        performance.setFibonacciPerformance(backtestFibonacciProjections(symbol));
        performance.setGannPerformance(backtestGannAnniversaries(symbol));
        performance.setConfluencePerformance(backtestConfluenceWindows(symbol));
        performance.setSolarImpact(analyzeSolarImpact(symbol));

        // Calculate overall metrics
        performance.setOverallScore(calculateOverallScore(performance));
        performance.setRecommendation(generateRecommendation(performance));

        log.info("✅ Comprehensive analysis complete for {}", symbol);

        return performance;
    }

    // ===== HELPER METHODS =====

    private Double getReturnForDays(HistoricalConfluenceWindow window, int days) {
        switch(days) {
            case 1: return window.getReturn1Day();
            case 3: return window.getReturn3Days();
            case 7: return window.getReturn7Days();
            case 14: return window.getReturn14Days();
            case 30: return window.getReturn30Days();
            default: return null;
        }
    }

    private boolean checkForReversal(int signalIndex, int futureIndex,
                                     List<BinanceHistoricalService.OHLCData> data) {
        if (signalIndex < 5 || futureIndex >= data.size() - 5) {
            return false;
        }

        // Calculate trend before signal (5-day slope)
        double preSlope = calculateSlope(data, signalIndex - 5, signalIndex);

        // Calculate trend after signal (5-day slope)
        double postSlope = calculateSlope(data, signalIndex, signalIndex + 5);

        // Reversal if slopes have opposite signs
        return (preSlope * postSlope) < 0;
    }

    private double calculateSlope(List<BinanceHistoricalService.OHLCData> data,
                                  int start, int end) {
        if (end - start < 2) return 0;

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        int n = end - start;

        for (int i = 0; i < n; i++) {
            double x = i;
            double y = data.get(start + i).close();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
    }

    private double calculateVolatility(int start, int end,
                                       List<BinanceHistoricalService.OHLCData> data) {
        if (end - start < 2) return 0;

        double sumReturns = 0;
        double sumSquaredReturns = 0;
        int count = 0;

        for (int i = start + 1; i < Math.min(end, data.size()); i++) {
            double prevClose = data.get(i - 1).close();
            double currClose = data.get(i).close();
            double dailyReturn = (currClose - prevClose) / prevClose;

            sumReturns += dailyReturn;
            sumSquaredReturns += dailyReturn * dailyReturn;
            count++;
        }

        if (count < 2) return 0;

        double mean = sumReturns / count;
        double variance = (sumSquaredReturns / count) - (mean * mean);

        return Math.sqrt(variance) * Math.sqrt(252); // Annualized
    }

    private List<PricePivot> findMajorPivotsInData(List<BinanceHistoricalService.OHLCData> data) {
        List<PricePivot> pivots = new ArrayList<>();

        if (data.size() < 20) return pivots;

        // Simple pivot detection (can enhance this)
        for (int i = 10; i < data.size() - 10; i++) {
            boolean isHigh = true;
            boolean isLow = true;
            double currentHigh = data.get(i).high();
            double currentLow = data.get(i).low();

            // Check surrounding 10 days
            for (int j = i - 10; j <= i + 10; j++) {
                if (j == i) continue;
                if (j >= 0 && j < data.size()) {
                    if (data.get(j).high() > currentHigh) isHigh = false;
                    if (data.get(j).low() < currentLow) isLow = false;
                }
            }

            if (isHigh || isLow) {
                LocalDate date = convertTimestampToDate(data.get(i).timestamp());
                double price = isHigh ? data.get(i).high() : data.get(i).low();
                String type = isHigh ? "HIGH" : "LOW";
                double strength = calculatePivotStrength(i, data, isHigh);

                pivots.add(new PricePivot(date, price, type, strength));
            }
        }

        return pivots;
    }

    private double calculatePivotStrength(int index,
                                          List<BinanceHistoricalService.OHLCData> data,
                                          boolean isHigh) {
        if (index < 5 || index >= data.size() - 5) return 0.5;

        double currentValue = isHigh ? data.get(index).high() : data.get(index).low();
        double minBefore = Double.MAX_VALUE;
        double maxBefore = Double.MIN_VALUE;

        // Look at 5 days before
        for (int i = 1; i <= 5; i++) {
            if (index - i >= 0) {
                double value = isHigh ? data.get(index - i).high() : data.get(index - i).low();
                minBefore = Math.min(minBefore, value);
                maxBefore = Math.max(maxBefore, value);
            }
        }

        // Look at 5 days after
        double minAfter = Double.MAX_VALUE;
        double maxAfter = Double.MIN_VALUE;
        for (int i = 1; i <= 5; i++) {
            if (index + i < data.size()) {
                double value = isHigh ? data.get(index + i).high() : data.get(index + i).low();
                minAfter = Math.min(minAfter, value);
                maxAfter = Math.max(maxAfter, value);
            }
        }

        if (isHigh) {
            // For high pivot: strength based on how much higher than surroundings
            double avgBefore = (minBefore + maxBefore) / 2;
            double avgAfter = (minAfter + maxAfter) / 2;
            double avgSurroundings = (avgBefore + avgAfter) / 2;

            return Math.min(1.0, (currentValue - avgSurroundings) / avgSurroundings * 5);
        } else {
            // For low pivot: strength based on how much lower than surroundings
            double avgBefore = (minBefore + maxBefore) / 2;
            double avgAfter = (minAfter + maxAfter) / 2;
            double avgSurroundings = (avgBefore + avgAfter) / 2;

            return Math.min(1.0, (avgSurroundings - currentValue) / avgSurroundings * 5);
        }
    }

    // ===== DATA MODELS =====

    @Data
    public static class HistoricalConfluenceWindow {
        private LocalDate date;
        private double priceAtSignal;
        private List<FibonacciSignal> fibSignals;
        private List<GannSignal> gannSignals;
        private List<SolarSignal> solarSignals;
        private int totalSignals;

        // Outcomes
        private Double return1Day;
        private Double return3Days;
        private Double return7Days;
        private Double return14Days;
        private Double return30Days;
        private boolean reversalOccurred;
        private double postSignalVolatility;

        public String getSignalTypes() {
            List<String> types = new ArrayList<>();
            if (fibSignals != null && !fibSignals.isEmpty()) types.add("Fibonacci");
            if (gannSignals != null && !gannSignals.isEmpty()) types.add("Gann");
            if (solarSignals != null && !solarSignals.isEmpty()) types.add("Solar");
            return String.join(" + ", types);
        }
    }

    @Data
    public static class FibonacciSignal {
        private double ratio;
        private int days;
        private PricePivot sourcePivot;
        private double accuracy; // How close to exact ratio (0 = perfect)
    }

    @Data
    public static class GannSignal {
        private int period;
        private int days;
        private PricePivot sourcePivot;
        private double accuracy; // How close to exact period
    }

    @Data
    public static class SolarSignal {
        private int apValue;
        private String stormLevel;
        private LocalDate date;
    }

    @Data
    public static class ConfluencePerformance {
        private String symbol;
        private int totalWindows;
        private Map<Integer, Double> averageReturns; // Days -> avg return %
        private Map<Integer, Double> successRates;   // Days -> success %
        private Map<Integer, Integer> sampleSizes;   // Days -> samples
        private Map<String, SignalTypePerformance> performanceBySignalType;
        private double overallSuccessRate;
        private double averageReturnAllWindows;
        private HistoricalConfluenceWindow bestWindow;
        private HistoricalConfluenceWindow worstWindow;
        private SignalEffectiveness signalEffectiveness;
        private Long timestamp;
    }

    @Data
    public static class SignalTypePerformance {
        private String signalType; // "Fibonacci", "Gann", "Solar", "Fibonacci+Gann", etc.
        private int count;
        private double avgReturn7Days;
        private double avgReturn30Days;
        private double successRate7Days;
        private double successRate30Days;
        private double volatilityRatio;
    }

    @Data
    public static class SignalEffectiveness {
        private double hitRate; // % of signals that led to >1% move in 7 days
        private double avgWinSize; // Average size of winning moves
        private double avgLossSize; // Average size of losing moves
        private double winLossRatio;
        private double sharpeRatio; // Risk-adjusted returns
        private double maxDrawdown; // Maximum peak-to-trough decline
    }

    @Data
    public static class SolarImpactAnalysis {
        private String symbol;
        private int highApDays;
        private double averageReturnHighAp;
        private double averageReturnNormal;
        private double volatilityRatio;
        private double correlationCoefficient;
        private String impactAssessment; // "Positive", "Negative", "Neutral"
    }

    @Data
    public static class TimeGeometryPerformance {
        private String symbol;
        private FibonacciPerformance fibonacciPerformance;
        private GannPerformance gannPerformance;
        private ConfluencePerformance confluencePerformance;
        private SolarImpactAnalysis solarImpact;
        private double overallScore; // 0-100
        private String recommendation; // "Strong Buy", "Buy", "Neutral", "Sell", "Strong Sell"
        private String confidenceLevel; // "High", "Medium", "Low"
    }

    // Helper methods for analysis
    private Map<String, SignalTypePerformance> analyzeBySignalType(
            List<HistoricalConfluenceWindow> windows) {
        Map<String, SignalTypePerformance> results = new HashMap<>();

        // Group windows by signal type combination
        Map<String, List<HistoricalConfluenceWindow>> grouped = windows.stream()
                .collect(Collectors.groupingBy(HistoricalConfluenceWindow::getSignalTypes));

        for (Map.Entry<String, List<HistoricalConfluenceWindow>> entry : grouped.entrySet()) {
            SignalTypePerformance perf = new SignalTypePerformance();
            perf.setSignalType(entry.getKey());
            perf.setCount(entry.getValue().size());

            // Calculate metrics for this signal type
            List<Double> returns7Days = entry.getValue().stream()
                    .map(w -> w.getReturn7Days())
                    .filter(r -> r != null)
                    .collect(Collectors.toList());

            List<Double> returns30Days = entry.getValue().stream()
                    .map(w -> w.getReturn30Days())
                    .filter(r -> r != null)
                    .collect(Collectors.toList());

            if (!returns7Days.isEmpty()) {
                perf.setAvgReturn7Days(returns7Days.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0));

                perf.setSuccessRate7Days(returns7Days.stream()
                        .filter(r -> r > 0)
                        .count() * 100.0 / returns7Days.size());
            }

            if (!returns30Days.isEmpty()) {
                perf.setAvgReturn30Days(returns30Days.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0));

                perf.setSuccessRate30Days(returns30Days.stream()
                        .filter(r -> r > 0)
                        .count() * 100.0 / returns30Days.size());
            }

            results.put(entry.getKey(), perf);
        }

        return results;
    }

    private double calculateOverallSuccessRate(List<HistoricalConfluenceWindow> windows) {
        long successful = windows.stream()
                .filter(w -> w.getReturn7Days() != null && w.getReturn7Days() > 0)
                .count();

        return windows.isEmpty() ? 0 : (successful * 100.0) / windows.size();
    }

    private double calculateAverageReturn(List<HistoricalConfluenceWindow> windows) {
        return windows.stream()
                .map(w -> w.getReturn7Days())
                .filter(r -> r != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private HistoricalConfluenceWindow findBestWindow(List<HistoricalConfluenceWindow> windows) {
        return windows.stream()
                .filter(w -> w.getReturn30Days() != null)
                .max(Comparator.comparingDouble(w -> w.getReturn30Days()))
                .orElse(null);
    }

    private HistoricalConfluenceWindow findWorstWindow(List<HistoricalConfluenceWindow> windows) {
        return windows.stream()
                .filter(w -> w.getReturn30Days() != null)
                .min(Comparator.comparingDouble(w -> w.getReturn30Days()))
                .orElse(null);
    }

    private SignalEffectiveness calculateEffectivenessMetrics(
            List<HistoricalConfluenceWindow> windows) {

        SignalEffectiveness effectiveness = new SignalEffectiveness();

        List<Double> returns7Days = windows.stream()
                .map(w -> w.getReturn7Days())
                .filter(r -> r != null)
                .collect(Collectors.toList());

        if (returns7Days.isEmpty()) {
            return effectiveness;
        }

        // Hit rate (signals leading to >1% move)
        long hits = returns7Days.stream()
                .filter(r -> Math.abs(r) > 1.0)
                .count();
        effectiveness.setHitRate((hits * 100.0) / returns7Days.size());

        // Win/Loss analysis
        List<Double> wins = returns7Days.stream()
                .filter(r -> r > 0)
                .collect(Collectors.toList());

        List<Double> losses = returns7Days.stream()
                .filter(r -> r < 0)
                .collect(Collectors.toList());

        if (!wins.isEmpty()) {
            effectiveness.setAvgWinSize(wins.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0));
        }

        if (!losses.isEmpty()) {
            effectiveness.setAvgLossSize(losses.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0));
        }

        if (!losses.isEmpty() && losses.stream().mapToDouble(Double::doubleValue).average().orElse(0) != 0) {
            effectiveness.setWinLossRatio(
                    effectiveness.getAvgWinSize() / Math.abs(effectiveness.getAvgLossSize()));
        }

        // Simple Sharpe ratio (assuming 0% risk-free rate)
        double avgReturn = returns7Days.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        double stdDev = Math.sqrt(returns7Days.stream()
                .mapToDouble(r -> Math.pow(r - avgReturn, 2))
                .average()
                .orElse(0.0));

        effectiveness.setSharpeRatio(stdDev > 0 ? avgReturn / stdDev : 0);

        // Max drawdown calculation (simplified)
        effectiveness.setMaxDrawdown(calculateMaxDrawdown(returns7Days));

        return effectiveness;
    }

    private double calculateMaxDrawdown(List<Double> returns) {
        if (returns.isEmpty()) return 0;

        double peak = 100.0; // Start with 100
        double maxDrawdown = 0;
        double current = 100.0;

        for (Double ret : returns) {
            current = current * (1 + ret/100.0);
            if (current > peak) {
                peak = current;
            }
            double drawdown = (peak - current) / peak * 100;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
            }
        }

        return maxDrawdown;
    }

    private double calculateOverallScore(TimeGeometryPerformance performance) {
        // Simple scoring algorithm
        double score = 50; // Start at neutral

        // Weight different components
        if (performance.getConfluencePerformance() != null) {
            double successRate = performance.getConfluencePerformance().getOverallSuccessRate();
            score += (successRate - 50) * 0.5; // Success rate impact
        }

        if (performance.getFibonacciPerformance() != null) {
            // Add Fibonacci performance impact
            // could extract key metrics from fibonacciPerformance
        }

        if (performance.getGannPerformance() != null) {
            // Add Gann performance impact
        }

        return Math.min(100, Math.max(0, score));
    }

    private String generateRecommendation(TimeGeometryPerformance performance) {
        double score = performance.getOverallScore();

        if (score >= 80) return "STRONG BUY";
        if (score >= 65) return "BUY";
        if (score >= 45) return "NEUTRAL";
        if (score >= 30) return "SELL";
        return "STRONG SELL";
    }

   // Helper method to find data index by date
    private int findDateIndex(List<BinanceHistoricalService.OHLCData> data, LocalDate targetDate) {
        for (int i = 0; i < data.size(); i++) {
            LocalDate dataDate = convertTimestampToDate(data.get(i).timestamp());
            if (dataDate.equals(targetDate)) {
                return i;
            }
        }
        return -1;
    }

    // Simple pivot detection (enhance with existing logic)
    private List<PricePivot> findHistoricalPivots(List<BinanceHistoricalService.OHLCData> data, int lookback) {
        List<PricePivot> pivots = new ArrayList<>();
        for (int i = lookback; i < data.size() - lookback; i++) {
            boolean isHigh = true;
            boolean isLow = true;
            double currentHigh = data.get(i).high();
            double currentLow = data.get(i).low();

            for (int j = i - lookback; j <= i + lookback; j++) {
                if (j == i) continue;
                if (data.get(j).high() > currentHigh) isHigh = false;
                if (data.get(j).low() < currentLow) isLow = false;
            }

            if (isHigh || isLow) {
                LocalDate date = convertTimestampToDate(data.get(i).timestamp());
                double price = isHigh ? data.get(i).high() : data.get(i).low();
                String type = isHigh ? "HIGH" : "LOW";
                pivots.add(new PricePivot(date, price, type, 0.8));
            }
        }
        return pivots;
    }


    // Calculate cycle performance with more metrics
    private Map<String, Object> analyzeGannCyclePerformance(List<Double> changes, int period) {
        Map<String, Object> stats = new HashMap<>();

        if (changes.isEmpty()) return stats;

        double sum = changes.stream().mapToDouble(Double::doubleValue).sum();
        double avg = changes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double max = changes.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double min = changes.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);

        // Calculate net move (sum of all returns)
        double netMove = sum;

        // Count positive vs negative returns
        long positiveCount = changes.stream().filter(c -> c > 0).count();
        long negativeCount = changes.stream().filter(c -> c < 0).count();

        stats.put("period", period);
        stats.put("netMove", netMove);
        stats.put("averageReturn", avg);
        stats.put("maxReturn", max);
        stats.put("minReturn", min);
        stats.put("positiveCount", positiveCount);
        stats.put("negativeCount", negativeCount);
        stats.put("totalEvents", changes.size());
        stats.put("successRate", (positiveCount * 100.0) / changes.size());

        return stats;
    }

    // Data class for results
    @Data
    public static class FibHitResult {
        private LocalDate pivotDate;
        private double pivotPrice;
        private String pivotType;
        private double fibonacciRatio;
        private LocalDate projectedDate;
        private LocalDate actualMoveDate;
        private double movePercent;
        private String direction;
        private boolean hit;
        private long daysFromProjection;

        public String getFormattedMove() {
            return String.format("%s %.2f%%", direction, Math.abs(movePercent));
        }

        public String getFibonacciLabel() {
            String ratioType = getRatioType(fibonacciRatio);
            return String.format("%s %.3f", ratioType, fibonacciRatio);
        }

        private String getRatioType(double ratio) {
            if (ratio == 0.333 || ratio == 0.667) return "Harmonic";
            if (ratio == 1.5 || ratio == 2.5) return "Geometric";
            if (ratio == 2.0) return "Double";
            if (ratio == 3.0) return "Triple";
            return "Fib";
        }
    }

}
