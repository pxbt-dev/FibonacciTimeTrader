package com.pxbt.dev.FibonacciTimeTrader.service;

import com.pxbt.dev.FibonacciTimeTrader.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TimeGeometryService {

    public static final int[] COMPREHENSIVE_GANN_PERIODS = {
            // Short-term cycles (divisions of 360)
            30,   // 30° - 1 month
            45,   // 45° - important angle
            60,   // 60° - 2 months
            72,   // 1/5 of 360
            90,   // 90° - quarter year
            120,  // 120° - 4 months
            135,  // 135° - 3/8 of circle
            144,  // MASTER NUMBER - Square of 12
            150,  // 5/12 of circle
            180,  // 180° - half year
            216,  // 3/5 of circle
            225,  // 225° - 5/8 of circle
            240,  // 240° - 2/3 of circle
            270,  // 270° - 3/4 of circle
            288,  // 4/5 of circle
            300,  // 300° - 5/6 of circle
            315,  // 315° - 7/8 of circle
            330,  // 330° - 11/12 of circle
            360,  // 360° - full year

            // Square of Seven cycles
            49,   // 7×7 - important weekly/monthly cycle
            98,   // 2×49
            147,  // 3×49
            196,  // 4×49

            // Medium-term (multiples of 360)
            540,  // 1.5 years
            720,  // 2 years
            900,  // 2.5 years
            1080, // 3 years
            1260, // 3.5 years
            1440  // 4 years
    };

    // For basic/standard use
    private static final int[] STANDARD_GANN_PERIODS = {30, 45, 60, 90, 120, 135, 144, 180, 225, 270, 315, 360, 540, 720};

    // For minimal/essential use
    private static final int[] ESSENTIAL_GANN_PERIODS = {90, 180, 360};

    @Autowired
    private BinanceHistoricalService binanceHistoricalService;

    @Autowired
    private SolarForecastService solarForecastService;

    @Autowired
    LunarDataService lunarDataService;

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

        log.info("🔍 Found {} major pivots for {}", majorPivots.size(), symbol);
        majorPivots.forEach(p ->
                log.info("   - {}: ${} on {}", p.getType(), p.getPrice(), p.getDate()));

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

        // DEBUG: Log Gann dates
        gannDates.forEach(gann ->
                log.info("   📅 {}: {} from {} (${})",
                        gann.getDate(), gann.getType(),
                        gann.getSourcePivot().getDate(),
                        gann.getSourcePivot().getPrice()));

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
        timeMap.put(3.000, "300 days (300% extension)");

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

            projection.setDescription(String.format("%s: %s from %s cycle high",
                    ratio == 0.333 || ratio == 0.667 ? "Harmonic" :
                            ratio == 1.5 || ratio == 2.5 ? "Geometric" :
                                    ratio == 2.0 ? "200% extension" :
                                            ratio == 3.0 ? "300% extension" :
                                                    ratio == 4.0 ? "400% extension" : "Fib",
                    daysLabel,
                    cycleHigh.getDate().format(DateTimeFormatter.ofPattern("MMM d, yyyy"))));

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
        switch (symbol) {
            case "BTC" -> {
                pivots.add(new PricePivot(LocalDate.of(2018, 12, 15), 3100.0, "MAJOR_LOW", 1.0));
                pivots.add(new PricePivot(LocalDate.of(2023, 1, 1), 15455.0, "MAJOR_LOW", 0.9));
                pivots.add(new PricePivot(LocalDate.of(2024, 3, 1), 72000.0, "MAJOR_HIGH", 0.8));
                pivots.add(new PricePivot(LocalDate.of(2025, 10, 1), 126272.76, "MAJOR_HIGH", 1.0));

                log.info("💰 BTC: Using 4 major cycle pivots");
                return pivots;
            }


            // SOL: Use known major pivots
            case "SOL" -> {
                pivots.add(new PricePivot(LocalDate.of(2020, 5, 11), 0.50, "MAJOR_LOW", 1.0));

                pivots.add(new PricePivot(LocalDate.of(2021, 11, 7), 258.00, "MAJOR_HIGH", 1.0));

                pivots.add(new PricePivot(LocalDate.of(2022, 12, 29), 8.00, "MAJOR_LOW", 0.9));

                pivots.add(new PricePivot(LocalDate.of(2024, 3, 18), 210.00, "MAJOR_HIGH", 0.8));

                pivots.add(new PricePivot(LocalDate.of(2025, 1, 19), 293.31, "MAJOR_HIGH", 1.0));


                log.info("☀️ SOL: Using 5 exact major cycle pivots");
                return pivots;
            }


            // TAO: Use known major pivots
            case "TAO" -> {
                pivots.add(new PricePivot(LocalDate.of(2023, 5, 14), 30.83, "MAJOR_LOW", 1.0));

                pivots.add(new PricePivot(LocalDate.of(2023, 10, 20), 47.91, "MAJOR_LOW", 0.8));

                pivots.add(new PricePivot(LocalDate.of(2023, 12, 16), 348.05, "MAJOR_HIGH", 0.9));

                pivots.add(new PricePivot(LocalDate.of(2024, 3, 7), 757.60, "MAJOR_HIGH", 1.0));


                log.info("🧠 TAO: Using 4 exact major cycle pivots");
                return pivots;
            }


            // WIF: Use known major pivots
            case "WIF" -> {
                pivots.add(new PricePivot(LocalDate.of(2023, 12, 13), 0.001555, "MAJOR_LOW", 1.0));

                pivots.add(new PricePivot(LocalDate.of(2024, 1, 4), 4.83, "MAJOR_HIGH", 1.0));

                pivots.add(new PricePivot(LocalDate.of(2024, 3, 15), 3.16, "MAJOR_HIGH", 0.8));

                pivots.add(new PricePivot(LocalDate.of(2024, 8, 6), 1.27, "MAJOR_LOW", 0.9));

                pivots.add(new PricePivot(LocalDate.of(2024, 11, 14), 4.19, "MAJOR_HIGH", 0.7));


                log.info("🐶 WIF: Using 5 exact major cycle pivots");
                return pivots;
            }
        }

        // For other symbols: Find 2-4 most significant pivots (fallback - should not happen for our 4 coins)
        if (monthlyData != null && monthlyData.size() >= 24) {
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

        log.info("📊 {}: Found {} major pivots", symbol, pivots.size());
        return pivots;
    }

    /**
     * Calculate ONLY MAJOR Gann dates (90, 180, 360 days from major pivots)
     */
    private List<GannDate> calculateMajorGannDates(List<PricePivot> majorPivots) {
        List<GannDate> gannDates = new ArrayList<>();

        for (PricePivot pivot : majorPivots) {
            // Only create Gann dates from recent pivots (last 5 years)
            if (pivot.getDate().isAfter(LocalDate.now().minusYears(2))) {
                // Use STANDARD periods for now - we can add toggle later
                for (int period : STANDARD_GANN_PERIODS) {
                    // Only add if the date is reasonable (within 5 years from pivot)
                    if (period <= 1440) { // Max 4 years for standard projections
                        gannDates.add(new GannDate(
                                pivot.getDate().plusDays(period),
                                period + "D_ANNIVERSARY",
                                pivot
                        ));
                    }
                }
            }
        }

        // Log how many Gann dates were created
        log.info("Created {} Gann dates from {} pivots", gannDates.size(), majorPivots.size());

        return gannDates;
    }

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
                    // Get the actual ratio, not just the rounded display
                    double actualRatio = p.getFibonacciRatio();
                    String ratioLabel = String.format("%.3f", actualRatio);

                    // Determine ratio type for signal naming
                    String ratioType = getFibRatioType(actualRatio);
                    String signal = "FIB_" + ratioLabel;

                    // Add additional metadata for lunar alignment detection
                    if (isMajorFibRatio(actualRatio)) {
                        signal = "FIB_MAJOR_" + ratioLabel;
                    }

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
                    // Extract period from type (e.g., "90D_ANNIVERSARY" -> "90")
                    String period = g.getType().replace("D_ANNIVERSARY", "");
                    String signal = "GANN " + period + "D";
                    signals.computeIfAbsent(g.getDate(), k -> new ArrayList<>())
                            .add(signal);
                    log.debug("🌀 Added Gann signal {} for {}", signal, g.getDate());
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

        // 4. Add Lunar events
        try {
            // Get lunar events for next 6 months
            LocalDate sixMonthsFromNow = LocalDate.now().plusMonths(6);

            // Convert LocalDate to Date for the service call
            Date startDate = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date endDate = Date.from(sixMonthsFromNow.atStartOfDay(ZoneId.systemDefault()).toInstant());

            List<LunarEvent> lunarEvents = lunarDataService.getEventsBetween(startDate, endDate);

            if (lunarEvents != null && !lunarEvents.isEmpty()) {
                log.info("🌙 Processing {} lunar events", lunarEvents.size());

                for (LunarEvent lunarEvent : lunarEvents) {
                    // Convert Date to LocalDate
                    LocalDate eventDate = lunarEvent.getDate().toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate();

                    // Only add future dates
                    if (!eventDate.isBefore(LocalDate.now())) {
                        String eventType = lunarEvent.getEventType().toUpperCase();
                        String signal = "LUNAR_" + eventType;

                        signals.computeIfAbsent(eventDate, k -> new ArrayList<>())
                                .add(signal);

                        log.debug("🌙 Added Lunar signal {} for {}",
                                signal, eventDate);
                    }
                }

                // Log counts
                long fullMoonCount = lunarEvents.stream()
                        .filter(e -> "FULL_MOON".equals(e.getEventType()))
                        .count();
                long newMoonCount = lunarEvents.stream()
                        .filter(e -> "NEW_MOON".equals(e.getEventType()))
                        .count();

                log.info("🌙 Found {} Full Moons and {} New Moons in next 6 months",
                        fullMoonCount, newMoonCount);
            }
        } catch (Exception e) {
            log.warn("Lunar data unavailable: {}", e.getMessage());
        }

        // 5. Detect Fibonacci-Lunar harmonic alignments
        try {
            List<LunarEvent> allLunarEvents = lunarDataService.getLunarEvents();

            for (FibonacciTimeProjection fib : timeProjections) {
                if (!fib.getDate().isBefore(LocalDate.now())) {
                    for (LunarEvent lunar : allLunarEvents) {
                        // Convert lunar Date to LocalDate
                        LocalDate lunarDate = lunar.getDate().toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate();

                        // Check if lunar event is within 1 day of Fibonacci date
                        long daysBetween = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                                fib.getDate(), lunarDate));

                        if (daysBetween <= 1) {
                            // This is a harmonic alignment
                            String harmonicSignal = "HARMONIC_FIB_" +
                                    String.format("%.3f", fib.getFibonacciRatio()) +
                                    "_LUNAR_" + lunar.getEventType().toUpperCase();

                            signals.computeIfAbsent(fib.getDate(), k -> new ArrayList<>())
                                    .add(harmonicSignal);

                            log.debug("🌀 Detected Fibonacci-Lunar harmonic: {} on {}",
                                    harmonicSignal, fib.getDate());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Fibonacci-Lunar harmonic detection skipped: {}", e.getMessage());
        }

        // ✅ 5. Detect Fibonacci-Lunar harmonic alignments
        detectFibonacciLunarHarmonics(signals, timeProjections);

        // Debug: Show all signal dates
        log.info("🌀 Total signal dates before filtering: {}", signals.size());
        if (log.isDebugEnabled()) {
            signals.forEach((date, sigList) -> {
                log.debug("🌀 Date {}: {} signals - {}", date, sigList.size(), sigList);
            });
        }

        // 6. Create vortex windows
        List<VortexWindow> windows = new ArrayList<>();
        for (Map.Entry<LocalDate, List<String>> entry : signals.entrySet()) {
            List<String> allSignals = entry.getValue();

            // Skip if only single signal type
            long solarCount = allSignals.stream()
                    .filter(s -> s.startsWith("SOLAR_AP_"))
                    .count();
            long lunarCount = allSignals.stream()
                    .filter(s -> s.startsWith("LUNAR_"))
                    .count();
            long fibGannCount = allSignals.size() - solarCount - lunarCount;

            // ✅ UPDATED: Enhanced confluence criteria
            boolean hasConfluence = false;

            // Criterion 1: At least 2 Fibonacci/Gann signals
            if (allSignals.size() >= 2 && fibGannCount >= 2) {
                hasConfluence = true;
            }
            // Criterion 2: Fibonacci/Gann + Lunar event
            else if (fibGannCount >= 1 && allSignals.stream().anyMatch(s -> s.startsWith("LUNAR_"))) {
                hasConfluence = true;
            }
            // Criterion 3: Fibonacci/Gann + Strong Solar
            else if (fibGannCount >= 1 && hasStrongSolarEvent(allSignals)) {
                hasConfluence = true;
            }
            // Criterion 4: Multiple lunar harmonics with Fibonacci
            else if (hasLunarFibonacciHarmonic(allSignals)) {
                hasConfluence = true;
            }

            if (hasConfluence) {
                VortexWindow window = new VortexWindow();
                window.setDate(entry.getKey());
                window.setIntensity(calculateVortexIntensity(allSignals));
                window.setType(determineVortexType(allSignals));
                window.setContributingFactors(allSignals);
                window.setDescription(buildVortexDescription(allSignals));

                windows.add(window);

                log.info("🌀 Created vortex window for {} with {} signals ({} fib/gann, {} solar, {} lunar): {}",
                        entry.getKey(), allSignals.size(), fibGannCount, solarCount, lunarCount,
                        allSignals.stream().limit(3).collect(Collectors.toList()));
            }
        }

        // Sort windows by intensity (highest first)
        windows.sort((a, b) -> Double.compare(b.getIntensity(), a.getIntensity()));

        log.info("🌀 Generated {} vortex windows total", windows.size());

        // Log strongest windows
        if (!windows.isEmpty()) {
            log.info("🌀 STRONGEST VORTEX WINDOWS:");
            windows.stream()
                    .limit(5)
                    .forEach(w -> log.info("   {}: {} intensity - {}",
                            w.getDate(),
                            String.format("%.2f", w.getIntensity()),
                            w.getDescription()));
        }

        return windows;
    }

    // Helper method to detect Fibonacci-Lunar harmonic alignments
    private void detectFibonacciLunarHarmonics(Map<LocalDate, List<String>> signals,
                                               List<FibonacciTimeProjection> fibProjections) {
        if (fibProjections == null || fibProjections.isEmpty()) return;

        try {
            // Get lunar events for the next year
            LocalDate oneYearFromNow = LocalDate.now().plusYears(1);
            Date startDate = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date endDate = Date.from(oneYearFromNow.atStartOfDay(ZoneId.systemDefault()).toInstant());

            List<LunarEvent> lunarEvents = lunarDataService.getEventsBetween(startDate, endDate);

            for (FibonacciTimeProjection fib : fibProjections) {
                if (!fib.getDate().isBefore(LocalDate.now())) {
                    for (LunarEvent lunar : lunarEvents) {
                        LocalDate lunarDate = lunar.getDate().toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate();

                        long daysBetween = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                                fib.getDate(), lunarDate));

                        if (daysBetween <= 1) {
                            // Create readable label
                            String fibLabel;
                            double ratio = fib.getFibonacciRatio();

                            if (Math.abs(ratio - 3.0) < 0.001) {
                                fibLabel = "Triple 3.000";
                            } else if (Math.abs(ratio - 2.0) < 0.001) {
                                fibLabel = "Double 2.000";
                            } else if (Math.abs(ratio - 1.618) < 0.001) {
                                fibLabel = "Golden 1.618";
                            } else if (Math.abs(ratio - 0.618) < 0.001) {
                                fibLabel = "Golden 0.618";
                            } else {
                                fibLabel = String.format("Fib %.3f", ratio);
                            }

                            String lunarType = lunar.getEventType().equals("FULL_MOON") ? "🌕 Full Moon" :
                                    (lunar.getEventType().equals("NEW_MOON") ? "🌑 New Moon" : lunar.getEventType());

                            // ONLY add the clean label, not the raw one
                            String harmonicSignal = fibLabel + " + " + lunarType;

                            signals.computeIfAbsent(fib.getDate(), k -> new ArrayList<>())
                                    .add(harmonicSignal);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Fibonacci-Lunar harmonic detection skipped: {}", e.getMessage());
        }
    }

    // Helper method to determine Fibonacci ratio type
    private String getFibRatioType(double ratio) {
        // Major Fibonacci ratios
        double[] majorRatios = {0.382, 0.5, 0.618, 0.786, 1.618, 2.618};
        for (double major : majorRatios) {
            if (Math.abs(ratio - major) < 0.001) {
                return "MAJOR";
            }
        }

        // Harmonic ratios (multiples of 1/3)
        if (Math.abs(ratio % (1.0/3.0)) < 0.001) {
            return "HARMONIC";
        }

        // Geometric ratios (multiples of 0.5)
        if (Math.abs(ratio % 0.5) < 0.001) {
            return "GEOMETRIC";
        }

        return "STANDARD";
    }

    // Check if Fibonacci ratio is major
    private boolean isMajorFibRatio(double ratio) {
        double[] majorRatios = {0.382, 0.5, 0.618, 0.786, 1.0, 1.272, 1.618, 2.618};
        for (double major : majorRatios) {
            if (Math.abs(ratio - major) < 0.001) {
                return true;
            }
        }
        return false;
    }

    // Check for major lunar events
    private boolean hasMajorLunarEvent(List<String> signals) {
        return signals.stream()
                .anyMatch(s -> s.startsWith("LUNAR_ECLIPSE") ||
                        s.startsWith("LUNAR_SUPER_MOON") ||
                        (s.startsWith("LUNAR_") && s.contains("HIGH")));
    }

    // Check for strong solar events
    private boolean hasStrongSolarEvent(List<String> signals) {
        return signals.stream()
                .anyMatch(s -> s.startsWith("SOLAR_AP_") &&
                        Integer.parseInt(s.replace("SOLAR_AP_", "")) >= 30);
    }

    // Check for lunar-Fibonacci harmonics
    private boolean hasLunarFibonacciHarmonic(List<String> signals) {
        long lunarCount = signals.stream()
                .filter(s -> s.startsWith("LUNAR_"))
                .count();
        long fibCount = signals.stream()
                .filter(s -> s.startsWith("FIB_"))
                .count();
        long harmonicCount = signals.stream()
                .filter(s -> s.startsWith("HARMONIC_"))
                .count();

        return (lunarCount >= 2 && fibCount >= 1) || harmonicCount >= 1;
    }

    // Enhanced vortex type determination
    private String determineVortexType(List<String> signals) {
        long fibCount = signals.stream().filter(s -> s.startsWith("FIB_")).count();
        long gannCount = signals.stream().filter(s -> s.startsWith("GANN_")).count();
        long lunarCount = signals.stream().filter(s -> s.startsWith("LUNAR_")).count();
        long solarCount = signals.stream().filter(s -> s.startsWith("SOLAR_AP_")).count();
        long harmonicCount = signals.stream().filter(s -> s.startsWith("HARMONIC_")).count();

        if (harmonicCount > 0) {
            return "HARMONIC_VORTEX";
        } else if (lunarCount >= 2 && (fibCount > 0 || gannCount > 0)) {
            return "LUNAR_VORTEX";
        } else if (fibCount >= 2 && gannCount >= 1) {
            return "MAJOR_RESONANCE";
        } else if (solarCount >= 1 && (fibCount > 0 || gannCount > 0)) {
            return "SOLAR_VORTEX";
        } else if (fibCount >= 2) {
            return "FIBONACCI_VORTEX";
        } else if (gannCount >= 2) {
            return "GANN_VORTEX";
        }

        return "STANDARD_VORTEX";
    }

    // Enhanced vortex intensity calculation
    private double calculateVortexIntensity(List<String> signals) {
        double base = signals.size() * 0.25; // Start with signal count

        // Boost for different signal types
        boolean hasFib = signals.stream().anyMatch(s -> s.startsWith("FIB_"));
        boolean hasGann = signals.stream().anyMatch(s -> s.startsWith("GANN_"));
        boolean hasSolar = signals.stream().anyMatch(s -> s.startsWith("SOLAR_AP_"));
        boolean hasLunar = signals.stream().anyMatch(s -> s.startsWith("LUNAR_"));
        boolean hasHarmonic = signals.stream().anyMatch(s -> s.startsWith("HARMONIC_"));

        // Type bonuses
        if (hasFib && hasGann) base += 0.3;
        if (hasHarmonic) base += 0.4;
        if (hasLunar && (hasFib || hasGann)) base += 0.2;
        if (hasSolar && (hasFib || hasGann)) base += 0.15;

        // Major event bonuses
        boolean hasMajorLunar = signals.stream()
                .anyMatch(s -> s.startsWith("LUNAR_ECLIPSE") || s.startsWith("LUNAR_SUPER_MOON"));
        boolean hasStrongSolar = signals.stream()
                .anyMatch(s -> s.startsWith("SOLAR_AP_") &&
                        Integer.parseInt(s.replace("SOLAR_AP_", "")) >= 30);

        if (hasMajorLunar) base += 0.25;
        if (hasStrongSolar) base += 0.2;

        // Major Fibonacci ratio bonus
        long majorFibCount = signals.stream()
                .filter(s -> s.startsWith("FIB_MAJOR_"))
                .count();
        base += majorFibCount * 0.1;

        // Cap at 0.95
        return Math.min(base, 0.95);
    }

    // Confluence description builder
    private String buildVortexDescription(List<String> signals) {
        long fibCount = signals.stream().filter(s -> s.startsWith("FIB_")).count();
        long gannCount = signals.stream().filter(s -> s.startsWith("GANN_")).count();
        long lunarCount = signals.stream().filter(s -> s.startsWith("LUNAR_")).count();
        long solarCount = signals.stream().filter(s -> s.startsWith("SOLAR_AP_")).count();
        long harmonicCount = signals.stream().filter(s -> s.startsWith("HARMONIC_")).count();

        List<String> parts = new ArrayList<>();

        if (fibCount > 0) parts.add(fibCount + " Fibonacci");
        if (gannCount > 0) parts.add(gannCount + " Gann");
        if (lunarCount > 0) parts.add(lunarCount + " Lunar");
        if (solarCount > 0) parts.add(solarCount + " Solar");
        if (harmonicCount > 0) parts.add(harmonicCount + " Harmonic");

        // Add specific lunar events if present
        signals.stream()
                .filter(s -> s.startsWith("LUNAR_ECLIPSE") || s.startsWith("LUNAR_SUPER_MOON"))
                .findFirst()
                .ifPresent(majorLunar -> {
                    parts.add(majorLunar.replace("LUNAR_", "").replace("_", " "));
                });

        // Add strong solar if present
        signals.stream()
                .filter(s -> s.startsWith("SOLAR_AP_") &&
                        Integer.parseInt(s.replace("SOLAR_AP_", "")) >= 30)
                .findFirst()
                .ifPresent(strongSolar -> {
                    parts.add("Strong Solar (AP ≥ 30)");
                });

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