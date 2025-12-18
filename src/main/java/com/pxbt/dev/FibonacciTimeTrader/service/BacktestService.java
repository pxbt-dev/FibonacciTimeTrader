package com.pxbt.dev.FibonacciTimeTrader.service;

import com.pxbt.dev.FibonacciTimeTrader.model.PricePivot;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
@Slf4j
public class BacktestService {

    private final TimeGeometryService timeGeometryService;
    private final BinanceHistoricalService binanceHistoricalService;

    public BacktestService(TimeGeometryService timeGeometryService,
                           BinanceHistoricalService binanceHistoricalService) {
        this.timeGeometryService = timeGeometryService;
        this.binanceHistoricalService = binanceHistoricalService;
    }

    /**
     * Test Fibonacci projections - FIXED to calculate real sample sizes
     */
    public FibonacciPerformance backtestFibonacciProjections(String symbol) {
        log.info("🔬 Backtesting Fibonacci & Harmonic projections for {}", symbol);

        List<BinanceHistoricalService.OHLCData> historicalData = binanceHistoricalService.getHistoricalData(symbol);

        if (historicalData == null || historicalData.size() < 500) {
            log.warn("Insufficient data for backtest: {} points",
                    historicalData != null ? historicalData.size() : 0);
            return createEmptyFibonacciPerformance(symbol);
        }

        // Use DOUBLE for ratios, store results keyed by ratio
        Map<Double, List<Double>> fibResults = new HashMap<>();

        // Updated to include Harmonic/Geometric ratios
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
                                        isRatio(ratio, 2.0) ? "Double" :
                                                isRatio(ratio, 3.0) ? "Triple" : "Fibonacci";

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

        // ✅ UPDATED: Test ALL your Gann cycles
        int[] gannPeriods = {30, 45, 60, 90, 120, 135, 144, 180, 225, 270, 315, 360, 540, 720};

        // 1. Find ACTUAL pivot points from your TimeGeometryService logic
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
                pivots.add(new PricePivot(LocalDate.of(2018, 12, 15), 3100.0, "MAJOR_LOW", 1.0));
                pivots.add(new PricePivot(LocalDate.of(2023, 1, 1), 15455.0, "MAJOR_LOW", 0.9));
                pivots.add(new PricePivot(LocalDate.of(2024, 3, 1), 72000.0, "MAJOR_HIGH", 0.8));
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

    /**
     * Test Vortex Windows (multiple signal convergences)
     */
    public VortexPerformance backtestConfluenceWindows(String symbol) {
        log.info("🔬 Backtesting Vortex Windows for {}", symbol);

        // This would simulate vortex window detection and check outcomes
        // For now, return placeholder indicating functionality to be implemented
        VortexPerformance performance = new VortexPerformance();
        performance.setSymbol(symbol);
        performance.setMessage("Vortex window backtesting requires complete historical vortex detection simulation");
        return performance;
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
    private int findDateIndex(
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
}