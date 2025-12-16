package com.pxbt.dev.FibonacciTimeTrader.service;

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
     * Comprehensive backtest of all time geometry signals
     */
    public BacktestResult backtestSymbol(String symbol, LocalDate startDate, LocalDate endDate) {
        log.info("🔬 Starting backtest for {} from {} to {}", symbol, startDate, endDate);

        List<BinanceHistoricalService.OHLCData> historicalData = binanceHistoricalService.getHistoricalData(symbol);

        BacktestResult result = new BacktestResult();
        result.setSymbol(symbol);
        result.setTestPeriod(startDate + " to " + endDate);

        // Track all signals and their outcomes
        List<SignalOutcome> signalOutcomes = new ArrayList<>();

        // Simulate running time geometry at historical dates
        for (BinanceHistoricalService.OHLCData data : historicalData) {
            LocalDate currentDate = toLocalDate(data.timestamp());

            if (!currentDate.isBefore(startDate) && !currentDate.isAfter(endDate)) {
                // Run time geometry analysis for this date
                signalOutcomes.addAll(analyzeDateOutcomes(currentDate, historicalData));
            }
        }

        // Analyze results
        result.setTotalSignals(signalOutcomes.size());
        result.setSignalOutcomes(signalOutcomes);
        result.calculateStatistics();

        log.info("✅ Backtest complete: {} signals analyzed", signalOutcomes.size());
        return result;
    }

    /**
     * Test specific signal types
     */
    public SignalPerformance backtestSignalType(String symbol, String signalType) {
        log.info("🔬 Backtesting {} signals for {}", signalType, symbol);

        List<BinanceHistoricalService.OHLCData> historicalData = binanceHistoricalService.getHistoricalData(symbol);
        List<SignalOutcome> outcomes = new ArrayList<>();

        for (int i = 0; i < historicalData.size() - 100; i++) { // Leave room for future outcomes
            LocalDate signalDate = toLocalDate(historicalData.get(i).timestamp());

            // Get actual price movement after signal date
            double priceAtSignal = historicalData.get(i).close();
            double priceAfter7Days = getPriceAfterDays(historicalData, i, 7);
            double priceAfter14Days = getPriceAfterDays(historicalData, i, 14);
            double priceAfter30Days = getPriceAfterDays(historicalData, i, 30);

            SignalOutcome outcome = new SignalOutcome();
            outcome.setSignalDate(signalDate);
            outcome.setSignalType(signalType);
            outcome.setPriceAtSignal(priceAtSignal);
            outcome.setPriceChange7Days(calculateChange(priceAtSignal, priceAfter7Days));
            outcome.setPriceChange14Days(calculateChange(priceAtSignal, priceAfter14Days));
            outcome.setPriceChange30Days(calculateChange(priceAtSignal, priceAfter30Days));
            outcome.setWasSignificant(isSignificantMove(priceAtSignal, priceAfter7Days));

            outcomes.add(outcome);
        }

        return analyzeSignalPerformance(signalType, outcomes);
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
        log.info("🔬 Backtesting Gann anniversaries for {}", symbol);

        List<BinanceHistoricalService.OHLCData> historicalData = binanceHistoricalService.getHistoricalData(symbol);

        if (historicalData == null || historicalData.size() < 400) {
            log.warn("Insufficient data for Gann backtest: {} points",
                    historicalData != null ? historicalData.size() : 0);
            return createEmptyGannPerformance(symbol);
        }

        // 1. Find ACTUAL pivot points (not every day!)
        List<LocalDate> pivotDates = findSignificantPivots(historicalData, 10);
        log.info("Found {} significant pivot points since {}",
                pivotDates.size(),
                pivotDates.isEmpty() ? "N/A" : pivotDates.get(0));

        // 2. Test anniversaries from these pivots
        Map<Integer, List<Double>> results = new HashMap<>();
        Map<Integer, Integer> sampleSizes = new HashMap<>();

        // Define Gann periods
        int[] gannPeriods = {90, 180, 360};

        for (int period : gannPeriods) {
            results.put(period, new ArrayList<>());
            sampleSizes.put(period, 0);
        }

        for (LocalDate pivotDate : pivotDates) {
            int pivotIndex = findDateIndex(historicalData, pivotDate);

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

        // 3. Calculate REALISTIC statistics
        Map<Integer, Double> avgReturns = new HashMap<>();
        Map<Integer, Double> successRates = new HashMap<>();

        for (int period : gannPeriods) {
            List<Double> changes = results.get(period);

            if (!changes.isEmpty()) {
                double avgReturn = changes.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);
                avgReturns.put(period, avgReturn);

                double successRate = changes.stream()
                        .filter(c -> c > 0)
                        .count() * 100.0 / changes.size();
                successRates.put(period, successRate);

                log.info("Gann {} days: {} samples, {}% success, {}% avg return",
                        period, changes.size(), successRate, avgReturn);
            }
        }

        // 4. Build response
        GannPerformance performance = new GannPerformance();
        performance.setSymbol(symbol);
        performance.setSampleSizes(sampleSizes);
        performance.setAverageReturns(avgReturns);
        performance.setSuccessRates(successRates);
        performance.setTimestamp(System.currentTimeMillis());

        return performance;
    }

    /**
     * Test Vortex Windows (multiple signal convergences)
     */
    public VortexPerformance backtestVortexWindows(String symbol) {
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

    private List<SignalOutcome> analyzeDateOutcomes(LocalDate date, List<BinanceHistoricalService.OHLCData> historicalData) {
        // Placeholder - would run actual time geometry for historical date
        // Then check actual price movements after each signal
        return new ArrayList<>();
    }

    private double getPriceAfterDays(List<BinanceHistoricalService.OHLCData> data, int startIndex, int days) {
        int targetIndex = Math.min(startIndex + days, data.size() - 1);
        return data.get(targetIndex).close();
    }

    private double calculateChange(double startPrice, double endPrice) {
        return ((endPrice - startPrice) / startPrice) * 100;
    }

    private boolean isSignificantMove(double startPrice, double endPrice) {
        double changePercent = Math.abs(calculateChange(startPrice, endPrice));
        return changePercent > 3.0; // 3% or more is "significant"
    }

    private SignalPerformance analyzeSignalPerformance(String signalType, List<SignalOutcome> outcomes) {
        SignalPerformance performance = new SignalPerformance();
        performance.setSignalType(signalType);
        performance.setTotalSignals(outcomes.size());

        if (outcomes.isEmpty()) return performance;

        // Calculate average returns
        double avg7Day = outcomes.stream()
                .mapToDouble(SignalOutcome::getPriceChange7Days)
                .average().orElse(0);
        double avg14Day = outcomes.stream()
                .mapToDouble(SignalOutcome::getPriceChange14Days)
                .average().orElse(0);
        double avg30Day = outcomes.stream()
                .mapToDouble(SignalOutcome::getPriceChange30Days)
                .average().orElse(0);

        // Calculate success rates
        long successful7Day = outcomes.stream()
                .filter(o -> o.getPriceChange7Days() > 0)
                .count();
        long successful14Day = outcomes.stream()
                .filter(o -> o.getPriceChange14Days() > 0)
                .count();
        long successful30Day = outcomes.stream()
                .filter(o -> o.getPriceChange30Days() > 0)
                .count();

        performance.setAverageReturn7Days(avg7Day);
        performance.setAverageReturn14Days(avg14Day);
        performance.setAverageReturn30Days(avg30Day);
        performance.setSuccessRate7Days((successful7Day * 100.0) / outcomes.size());
        performance.setSuccessRate14Days((successful14Day * 100.0) / outcomes.size());
        performance.setSuccessRate30Days((successful30Day * 100.0) / outcomes.size());

        return performance;
    }

    private FibStats calculateFibStats(int fibNumber, List<Double> changes) {
        FibStats stats = new FibStats();
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

    private LocalDate toLocalDate(long timestamp) {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
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
    public static class BacktestResult {
        private String symbol;
        private String testPeriod;
        private int totalSignals;
        private List<SignalOutcome> signalOutcomes;
        private double overallSuccessRate;
        private double averageReturn;

        public void calculateStatistics() {
            if (signalOutcomes == null || signalOutcomes.isEmpty()) {
                overallSuccessRate = 0;
                averageReturn = 0;
                return;
            }

            // Calculate average 7-day return
            averageReturn = signalOutcomes.stream()
                    .mapToDouble(SignalOutcome::getPriceChange7Days)
                    .average().orElse(0);

            // Calculate success rate (positive returns)
            long successful = signalOutcomes.stream()
                    .filter(o -> o.getPriceChange7Days() > 0)
                    .count();
            overallSuccessRate = (successful * 100.0) / signalOutcomes.size();
        }
    }

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

        // Helper to get label
        public String getRatioLabel() {
            return String.format("Fib %.3f", ratio);
        }

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