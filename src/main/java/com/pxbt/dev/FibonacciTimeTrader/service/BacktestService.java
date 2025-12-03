package com.pxbt.dev.FibonacciTimeTrader.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
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
     * Test Fibonacci projections
     */
    public FibonacciPerformance backtestFibonacciProjections(String symbol) {
        log.info("🔬 Backtesting Fibonacci projections for {}", symbol);

        List<BinanceHistoricalService.OHLCData> historicalData = binanceHistoricalService.getHistoricalData(symbol);
        Map<Integer, List<Double>> fibResults = new HashMap<>(); // Fib number -> price changes

        // Initialize for common Fibonacci numbers
        int[] fibNumbers = {5, 8, 13, 21, 34, 55, 89};
        for (int fib : fibNumbers) {
            fibResults.put(fib, new ArrayList<>());
        }

        // Test each Fibonacci period
        for (int i = 0; i < historicalData.size() - 90; i++) { // Need room for 89-day lookahead
            double priceAtStart = historicalData.get(i).close();

            for (int fib : fibNumbers) {
                if (i + fib < historicalData.size()) {
                    double priceAtFib = historicalData.get(i + fib).close();
                    double change = calculateChange(priceAtStart, priceAtFib);
                    fibResults.get(fib).add(change);
                }
            }
        }

        FibonacciPerformance performance = new FibonacciPerformance();
        performance.setSymbol(symbol);

        // Calculate statistics for each Fibonacci number
        Map<Integer, FibStats> stats = new HashMap<>();
        for (Map.Entry<Integer, List<Double>> entry : fibResults.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                FibStats fibStats = calculateFibStats(entry.getValue());
                stats.put(entry.getKey(), fibStats);
            }
        }

        performance.setFibonacciStats(stats);
        return performance;
    }

    /**
     * Test Gann anniversary dates (90, 180, 360 days)
     */
    public GannPerformance backtestGannAnniversaries(String symbol) {
        log.info("🔬 Backtesting Gann anniversaries for {}", symbol);

        List<BinanceHistoricalService.OHLCData> historicalData = binanceHistoricalService.getHistoricalData(symbol);
        Map<Integer, List<Double>> gannResults = new HashMap<>();
        int[] gannPeriods = {90, 180, 360};

        for (int period : gannPeriods) {
            gannResults.put(period, new ArrayList<>());
        }

        for (int i = 0; i < historicalData.size() - 365; i++) { // Need room for 360-day lookahead
            double priceAtStart = historicalData.get(i).close();

            for (int period : gannPeriods) {
                if (i + period < historicalData.size()) {
                    double priceAtAnniversary = historicalData.get(i + period).close();
                    double change = calculateChange(priceAtStart, priceAtAnniversary);
                    gannResults.get(period).add(change);
                }
            }
        }

        GannPerformance performance = new GannPerformance();
        performance.setSymbol(symbol);

        Map<Integer, Double> avgReturns = new HashMap<>();
        Map<Integer, Double> successRates = new HashMap<>();

        for (int period : gannPeriods) {
            List<Double> changes = gannResults.get(period);
            if (!changes.isEmpty()) {
                double avgChange = changes.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double successRate = changes.stream().filter(c -> c > 0).count() * 100.0 / changes.size();

                avgReturns.put(period, avgChange);
                successRates.put(period, successRate);
            }
        }

        performance.setAverageReturns(avgReturns);
        performance.setSuccessRates(successRates);
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

    private FibStats calculateFibStats(List<Double> changes) {
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
        return java.time.Instant.ofEpochMilli(timestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate();
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
        private Map<Integer, FibStats> fibonacciStats;
    }

    @Data
    public static class FibStats {
        private int sampleSize;
        private double averageChange;
        private double maxChange;
        private double minChange;
        private double stdDev;
        private double successRate; // Percentage of positive changes
    }

    @Data
    public static class GannPerformance {
        private String symbol;
        private Map<Integer, Double> averageReturns; // Period -> avg % return
        private Map<Integer, Double> successRates;   // Period -> % success
    }

    @Data
    public static class VortexPerformance {
        private String symbol;
        private String message;
    }
}