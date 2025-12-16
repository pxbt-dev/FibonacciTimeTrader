package com.pxbt.dev.FibonacciTimeTrader.service;

import com.pxbt.dev.FibonacciTimeTrader.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Service
public class TimeGeometryService {

    @Autowired
    private BinanceHistoricalService binanceHistoricalService;

    @Autowired
    private SolarForecastService solarForecastService;

    public VortexAnalysis analyzeSymbol(String symbol) {
        log.info("⏰ MAJOR CYCLE ANALYSIS FOR {}", symbol);

        // Get historical data
        List<BinanceHistoricalService.OHLCData> historicalData = binanceHistoricalService.getHistoricalData(symbol);
        VortexAnalysis analysis = new VortexAnalysis();
        analysis.setSymbol(symbol);

        if (historicalData == null || historicalData.isEmpty()) {
            log.warn("⚠️ No data for {}", symbol);
            return analysis;
        }

        // Get MONTHLY data for major cycles
        List<BinanceHistoricalService.OHLCData> monthlyData = binanceHistoricalService.getMonthlyData(symbol);

        // STEP 1: Get or create MAJOR pivots
        List<PricePivot> majorPivots = getMajorCyclePivots(symbol, monthlyData);

        // Find cycle high and low
        PricePivot cycleHigh = majorPivots.stream()
                .filter(p -> p.getType().contains("HIGH"))
                .max(Comparator.comparing(PricePivot::getDate))
                .orElse(null);

        PricePivot cycleLow = majorPivots.stream()
                .filter(p -> p.getType().contains("LOW"))
                .max(Comparator.comparing(PricePivot::getDate))
                .orElse(null);

        // STEP 2: Generate PURE TIME projections (when)
        List<FibonacciTimeProjection> timeProjections = generateTimeProjections(cycleHigh);
        analysis.setFibonacciTimeProjections(timeProjections);

        // STEP 3: Generate PRICE levels (where)
        List<FibonacciPriceLevel> priceLevels = generatePriceLevels(cycleHigh, cycleLow);
        analysis.setFibonacciPriceLevels(priceLevels);

        // STEP 4: Get Gann dates
        List<GannDate> gannDates = calculateMajorGannDates(majorPivots);
        analysis.setGannDates(gannDates);

        // STEP 5: Generate vortex windows (alignment dates)
        List<VortexWindow> vortexWindows = identifyMajorVortexWindows(timeProjections, gannDates);
        analysis.setVortexWindows(vortexWindows);

        // STEP 6: Other metrics
        analysis.setCompressionScore(calculateCompressionScore(historicalData));
        analysis.setConfidenceScore(calculateMajorConfidenceScore(majorPivots));

        // Log summary
        log.info("✅ {}: {} time projections, {} price levels, {} Gann dates, {} vortex windows",
                symbol, timeProjections.size(), priceLevels.size(),
                gannDates.size(), vortexWindows.size());

        return analysis;
    }

    /**
     * Generate TIME projections only (when)
     */
    private List<FibonacciTimeProjection> generateTimeProjections(PricePivot cycleHigh) {
        List<FibonacciTimeProjection> projections = new ArrayList<>();

        if (cycleHigh == null) {
            log.warn("No cycle high for time projections");
            return projections;
        }

        log.info("🔮 Generating time projections from cycle high: {} at ${}",
                cycleHigh.getDate(), cycleHigh.getPrice());

        // Time ratios (days) - Now includes Harmonic/Geometric levels
        Map<Double, String> timeMap = new LinkedHashMap<>(); // LinkedHashMap maintains insertion order
        timeMap.put(0.236, "24 days");
        timeMap.put(0.382, "38 days");
        timeMap.put(0.500, "50 days");
        timeMap.put(0.618, "62 days");
        timeMap.put(0.786, "79 days");
        timeMap.put(1.000, "100 days");
        timeMap.put(1.272, "127 days");
        timeMap.put(1.618, "162 days");
        timeMap.put(2.618, "262 days");
        // Harmonic/Geometric ratios (1/3 series)
        timeMap.put(0.333, "33 days (Harmonic 1/3)");
        timeMap.put(0.667, "67 days (Harmonic 2/3)");
        timeMap.put(1.333, "133 days (Harmonic 1.333)");
        timeMap.put(1.500, "150 days (Geometric 1.5)");
        timeMap.put(1.667, "167 days (Harmonic 1.667)");
        timeMap.put(2.000, "200 days (Double)");
        timeMap.put(2.333, "233 days (Harmonic 2.333)");
        timeMap.put(2.500, "250 days (Geometric 2.5)");
        timeMap.put(2.667, "267 days (Harmonic 2.667)");
        timeMap.put(3.000, "300 days (Triple)");

        for (Map.Entry<Double, String> entry : timeMap.entrySet()) {
            double ratio = entry.getKey();
            String daysLabel = entry.getValue();

            int days = (int) Math.round(100 * ratio);
            LocalDate date = cycleHigh.getDate().plusDays(days);

            if (date.isBefore(LocalDate.now())) continue;

            FibonacciTimeProjection projection = new FibonacciTimeProjection();
            projection.setDate(date);
            projection.setFibonacciNumber(days);
            projection.setFibonacciRatio(ratio);
            projection.setSourcePivot(cycleHigh);

            // Set intensity based on ratio type
            double intensity = calculateFibIntensity(ratio);
            if (ratio == 0.333 || ratio == 0.667) {
                intensity = 0.75; // Higher intensity for key harmonic levels
            }

            projection.setIntensity(intensity);
            projection.setType("TIME_PROJECTION");
            projection.setPriceTarget(0);

            projection.setDescription(String.format("%s: %s from %s high",
                    ratio == 0.333 || ratio == 0.667 ? "Harmonic" :
                            ratio == 1.5 || ratio == 2.5 ? "Geometric" : "Fib",
                    daysLabel, cycleHigh.getDate()));

            projections.add(projection);
        }

        log.info("🔮 Generated {} time projections", projections.size());
        return projections;
    }

    /**
     * Generate all price levels (support and resistance)
     */
    private List<FibonacciPriceLevel> generatePriceLevels(PricePivot cycleHigh, PricePivot cycleLow) {
        List<FibonacciPriceLevel> allLevels = new ArrayList<>();

        if (cycleHigh == null || cycleLow == null) return allLevels;

        double highPrice = cycleHigh.getPrice();
        double lowPrice = cycleLow.getPrice();

        // retracement levels
        allLevels.addAll(generateRetracementLevels(highPrice, lowPrice));

        // extension levels
        allLevels.addAll(generateExtensionLevels(highPrice, lowPrice));

        // Sort by price (highest to lowest for display)
        allLevels.sort((a, b) -> Double.compare(b.getPrice(), a.getPrice()));

        log.info("📊 Generated {} Fibonacci price levels ({} support, {} resistance)",
                allLevels.size(),
                allLevels.stream().filter(l -> l.getType().equals("SUPPORT")).count(),
                allLevels.stream().filter(l -> l.getType().equals("RESISTANCE")).count());

        return allLevels;
    }

    /**
     * Generate Fibonacci retracement levels (support)
     */
    private List<FibonacciPriceLevel> generateRetracementLevels(double highPrice, double lowPrice) {
        List<FibonacciPriceLevel> levels = new ArrayList<>();
        double range = highPrice - lowPrice;

        // Standard Fibonacci retracement levels plus Harmonic levels
        double[] retracements = {0.000, 0.236, 0.333, 0.382, 0.500, 0.618, 0.667, 0.786, 1.000};
        String[] labels = {
                "Cycle High (0%)",
                "Fib 0.236 (23.6%)",
                "Harmonic 0.333 (33.3%)",
                "Fib 0.382 (38.2%)",
                "Fib 0.500 (50.0%)",
                "Fib 0.618 (61.8%)",
                "Harmonic 0.667 (66.7%)",
                "Fib 0.786 (78.6%)",
                "Cycle Low (100%)"
        };

        for (int i = 0; i < retracements.length; i++) {
            double ratio = retracements[i];
            double price = highPrice - (range * ratio);
            String distance = String.format("%.1f%% retracement", ratio * 100);

            levels.add(new FibonacciPriceLevel(
                    price, ratio, labels[i], "SUPPORT", distance
            ));
        }

        return levels;
    }

    /**
     * Generate Fibonacci extension levels (resistance)
     */
    private List<FibonacciPriceLevel> generateExtensionLevels(double highPrice, double lowPrice) {
        List<FibonacciPriceLevel> levels = new ArrayList<>();
        double range = highPrice - lowPrice;

        // Fibonacci extension levels plus Harmonic/Geometric extensions
        double[] extensions = {
                1.272, 1.333, 1.382, 1.500, 1.618, 1.667,
                2.000, 2.333, 2.500, 2.618, 2.667,
                3.000, 3.333, 3.500, 3.618, 3.667,
                4.000, 4.236, 4.333, 4.500
        };
        String[] labels = {
                "Fib 1.272 (27.2% ext)",
                "Harmonic 1.333 (33.3% ext)",
                "Fib 1.382 (38.2% ext)",
                "Geometric 1.500 (50.0% ext)",
                "Fib 1.618 (61.8% ext)",
                "Harmonic 1.667 (66.7% ext)",
                "Double 2.000 (100% ext)",
                "Harmonic 2.333 (133% ext)",
                "Geometric 2.500 (150% ext)",
                "Fib 2.618 (161.8% ext)",
                "Harmonic 2.667 (167% ext)",
                "Triple 3.000 (200% ext)",
                "Harmonic 3.333 (233% ext)",
                "Geometric 3.500 (250% ext)",
                "Fib 3.618 (261.8% ext)",
                "Harmonic 3.667 (267% ext)",
                "Quadruple 4.000 (300% ext)",
                "Fib 4.236 (323.6% ext)",
                "Harmonic 4.333 (333% ext)",
                "Geometric 4.500 (350% ext)"
        };

        for (int i = 0; i < extensions.length; i++) {
            double ratio = extensions[i];
            double price = highPrice + (range * (ratio - 1.0));
            double extensionPercent = (ratio - 1.0) * 100;
            String distance = String.format("%.1f%% extension", extensionPercent);

            // Skip if price is unrealistic (more than 5x the high)
            if (price <= highPrice * 5) {
                levels.add(new FibonacciPriceLevel(
                        price, ratio, labels[i], "RESISTANCE", distance
                ));
            }
        }

        return levels;
    }

    /**
     * Get MAJOR cycle pivots (focus on key levels only)
     */
    public List<PricePivot> getMajorCyclePivots(String symbol, List<BinanceHistoricalService.OHLCData> monthlyData) {
        List<PricePivot> pivots = new ArrayList<>();

        // For BTC: Use known major pivots
        if (symbol.equals("BTC")) {
            pivots.add(new PricePivot(LocalDate.of(2018, 12, 15), 3100.0, "MAJOR_LOW", 1.0));
            pivots.add(new PricePivot(LocalDate.of(2023, 1, 1), 15455.0, "MAJOR_LOW", 0.9));
            pivots.add(new PricePivot(LocalDate.of(2024, 3, 1), 72000.0, "MAJOR_HIGH", 0.8));
            pivots.add(new PricePivot(LocalDate.of(2025, 10, 1), 126272.76, "MAJOR_HIGH", 1.0));

            log.info("💰 BTC: Using 4 major cycle pivots");
            return pivots;
        }

        // For other coins: Find 2-4 most significant pivots
        if (monthlyData.size() >= 24) { // Need 2+ years
            // Find highest high and lowest low
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
        }

        return pivots;
    }

    /**
     * Calculate ONLY MAJOR Gann dates (90, 180, 360 days from major pivots)
     */
    private List<GannDate> calculateMajorGannDates(List<PricePivot> majorPivots) {
        List<GannDate> gannDates = new ArrayList<>();

        for (PricePivot pivot : majorPivots) {
            // Only create Gann dates from recent pivots (last 5 years)
            if (pivot.getDate().isAfter(LocalDate.now().minusYears(5))) {
                gannDates.add(new GannDate(pivot.getDate().plusDays(90), "90D_ANNIVERSARY", pivot));
                gannDates.add(new GannDate(pivot.getDate().plusDays(180), "180D_ANNIVERSARY", pivot));
                gannDates.add(new GannDate(pivot.getDate().plusDays(360), "360D_ANNIVERSARY", pivot));
            }
        }

        return gannDates;
    }

    /**
     * Enhanced vortex windows with proper solar integration
     */
    private List<VortexWindow> identifyMajorVortexWindows(
            List<FibonacciTimeProjection> timeProjections,
            List<GannDate> gannDates) {

        log.info("🌀 Creating vortex windows...");

        Map<LocalDate, List<String>> signals = new HashMap<>();

        // 1. Add Fibonacci time projections
        if (timeProjections != null) {
            log.info("🌀 Processing {} time projections", timeProjections.size());
            timeProjections.forEach(p -> {
                if (!p.getDate().isBefore(LocalDate.now())) {
                    String ratioLabel = String.format("%.3f", p.getFibonacciRatio());
                    String signal = "FIB_" + ratioLabel;
                    signals.computeIfAbsent(p.getDate(), k -> new ArrayList<>())
                            .add(signal);
                    log.debug("🌀 Added Fibonacci signal {} for {}", signal, p.getDate());
                }
            });
        } else {
            log.warn("🌀 No time projections provided!");
        }

        // 2. Add Gann dates
        if (gannDates != null) {
            log.info("🌀 Processing {} Gann dates", gannDates.size());
            gannDates.forEach(g -> {
                if (!g.getDate().isBefore(LocalDate.now())) {
                    signals.computeIfAbsent(g.getDate(), k -> new ArrayList<>())
                            .add(g.getType());
                    log.debug("🌀 Added Gann signal {} for {}", g.getType(), g.getDate());
                }
            });
        } else {
            log.warn("🌀 No Gann dates provided!");
        }

        // 3. Add STRONG Solar dates (AP ≥ 20 only)
        try {
            List<ForecastDay> highApDays = solarForecastService.getHighApDates();
            if (highApDays != null) {
                log.info("🌀 Processing {} solar days", highApDays.size());
                for (ForecastDay solarDay : highApDays) {
                    if (solarDay.getAp() >= 20 && !solarDay.getDate().isBefore(LocalDate.now())) {
                        String signal = "SOLAR_AP_" + solarDay.getAp();
                        signals.computeIfAbsent(solarDay.getDate(), k -> new ArrayList<>())
                                .add(signal);
                        log.debug("🌀 Added Solar signal {} for {}", signal, solarDay.getDate());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Solar data unavailable: {}", e.getMessage());
        }

        // Debug: Show all signal dates
        log.info("🌀 Total signal dates: {}", signals.size());
        signals.forEach((date, sigList) -> {
            log.debug("🌀 Date {}: {} signals - {}", date, sigList.size(), sigList);
        });

        // 4. Create vortex windows
        List<VortexWindow> windows = new ArrayList<>();
        for (Map.Entry<LocalDate, List<String>> entry : signals.entrySet()) {
            List<String> allSignals = entry.getValue();

            // Skip if only solar signals
            long solarCount = allSignals.stream()
                    .filter(s -> s.startsWith("SOLAR_AP_"))
                    .count();
            long fibGannCount = allSignals.size() - solarCount;

            if (allSignals.size() >= 2 && fibGannCount >= 1) {
                VortexWindow window = new VortexWindow();
                window.setDate(entry.getKey());
                window.setIntensity(calculateVortexIntensity(allSignals));
                window.setType("VORTEX_WINDOW");
                window.setContributingFactors(allSignals);
                window.setDescription(buildVortexDescription(allSignals));

                windows.add(window);

                log.info("🌀 Created vortex window for {} with {} signals: {}",
                        entry.getKey(), allSignals.size(), allSignals);
            }
        }

        log.info("🌀 Generated {} vortex windows total", windows.size());
        return windows;
    }

    /**
     * Calculate vortex intensity based on signal composition
     */
    private double calculateVortexIntensity(List<String> signals) {
        double base = signals.size() * 0.3;

        // Boost for Fibonacci/Gann combinations
        boolean hasFib = signals.stream().anyMatch(s -> s.startsWith("FIB_"));
        boolean hasGann = signals.stream().anyMatch(s -> s.contains("ANNIVERSARY"));
        boolean hasSolar = signals.stream().anyMatch(s -> s.startsWith("SOLAR_AP_"));

        if (hasFib && hasGann) base += 0.2;
        if (hasSolar && (hasFib || hasGann)) base += 0.1;

        return Math.min(base, 0.95);
    }

    /**
     * Build vortex description based on signal types
     */
    private String buildVortexDescription(List<String> signals) {
        long fibCount = signals.stream().filter(s -> s.startsWith("FIB_")).count();
        long gannCount = signals.stream().filter(s -> s.contains("_ANNIVERSARY")).count();
        long solarCount = signals.stream().filter(s -> s.startsWith("SOLAR_AP_")).count();

        List<String> parts = new ArrayList<>();

        if (fibCount > 0) parts.add(fibCount + " Fibonacci");
        if (gannCount > 0) parts.add(gannCount + " Gann");
        if (solarCount > 0) parts.add(solarCount + " Solar");

        return String.join(" • ", parts) + " confluence";
    }

    /**
     * Calculate compression score from historical data volatility
     */
    private double calculateCompressionScore(List<BinanceHistoricalService.OHLCData> historicalData) {
        if (historicalData.size() < 20) return 0.5;

        double totalVolatility = 0;
        int lookback = Math.min(20, historicalData.size());

        for (int i = historicalData.size() - lookback; i < historicalData.size(); i++) {
            BinanceHistoricalService.OHLCData candle = historicalData.get(i);
            double range = (candle.high() - candle.low()) / candle.close();
            totalVolatility += range;
        }

        double avgVolatility = totalVolatility / lookback;
        return Math.min(1.0, 1.0 / (avgVolatility + 0.01));
    }

    /**
     * Simplified confidence score based on pivot quality
     */
    private double calculateMajorConfidenceScore(List<PricePivot> majorPivots) {
        if (majorPivots.size() >= 4) return 0.9;
        if (majorPivots.size() >= 2) return 0.7;
        return 0.5;
    }

    /**
     * Calculate Fibonacci intensity based on ratio importance
     */
    private double calculateFibIntensity(double ratio) {
        // Key Fibonacci levels
        if (ratio == 0.618 || ratio == 1.618) return 0.9;
        if (ratio == 0.382 || ratio == 0.786) return 0.7;
        if (ratio == 0.500) return 0.8;
        if (ratio == 1.272 || ratio == 2.618) return 0.6;

        // Key Harmonic/Geometric levels
        if (ratio == 0.333 || ratio == 0.667) return 0.75;
        if (ratio == 1.333 || ratio == 1.667) return 0.65;
        if (ratio == 1.5 || ratio == 2.5 || ratio == 3.5) return 0.7;
        if (ratio == 2.333 || ratio == 2.667) return 0.6;
        if (ratio == 2.0) return 0.8;
        if (ratio == 3.0) return 0.7;

        return 0.5;
    }

    /**
     * Helper to convert timestamp to LocalDate
     */
    private LocalDate convertTimestampToDate(long timestamp) {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

}