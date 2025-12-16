package com.pxbt.dev.FibonacciTimeTrader.controller;

import com.pxbt.dev.FibonacciTimeTrader.model.GannDate;
import com.pxbt.dev.FibonacciTimeTrader.model.PricePivot;
import com.pxbt.dev.FibonacciTimeTrader.model.VortexAnalysis;
import com.pxbt.dev.FibonacciTimeTrader.service.BinanceHistoricalService;
import com.pxbt.dev.FibonacciTimeTrader.service.TimeGeometryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static com.pxbt.dev.FibonacciTimeTrader.service.TimeGeometryService.COMPREHENSIVE_GANN_PERIODS;

@Slf4j
@RestController
@RequestMapping("/api/gann")
public class GannDateController {

    @Autowired
    private BinanceHistoricalService binanceHistoricalService;

    @Autowired
    private TimeGeometryService timeGeometryService;

    private static final int[] GANN_PERIODS = {30, 45, 60, 90, 120, 135, 144, 180, 225, 270, 315, 360, 540, 720};
    private static final int PIVOT_LOOKBACK_DAYS = 10;

    /**
     * Get pure Gann dates for a symbol
     */

    @GetMapping("/dates/{symbol}")
    public ResponseEntity<?> getGannDates(@PathVariable String symbol) {
        try {
            log.info("📅 Fetching MAJOR Gann dates for {}", symbol);

            // Get monthly data for major cycle detection
            List<BinanceHistoricalService.OHLCData> monthlyData =
                    binanceHistoricalService.getMonthlyData(symbol);

            if (monthlyData == null || monthlyData.isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            // 1. Get ONLY MAJOR cycle pivots (reusing logic from TimeGeometryService)
            List<PricePivot> majorPivots = getMajorCyclePivots(symbol, monthlyData);

            if (majorPivots.isEmpty()) {
                log.warn("⚠️ No major pivots found for {}", symbol);
                return ResponseEntity.ok(Collections.emptyList());
            }

            log.info("🎯 Found {} major pivots for {} Gann dates", majorPivots.size(), symbol);

            // 2. Generate Gann dates ONLY from these major pivots
            List<GannDate> gannDates = generateGannDatesFromMajorPivots(majorPivots);

            // 3. Filter to future dates and limit
            LocalDate today = LocalDate.now();
            List<GannDate> futureGannDates = gannDates.stream()
                    .filter(gann -> !gann.getDate().isBefore(today))
                    .sorted(Comparator.comparing(GannDate::getDate))
                    .limit(15) // Show next 15 dates
                    .collect(Collectors.toList());

            log.info("✅ Generated {} Gann dates for {} ({} future)",
                    gannDates.size(), symbol, futureGannDates.size());

            // 4. Log the dates for debugging
            if (!futureGannDates.isEmpty()) {
                log.info("📅 MAJOR Gann Dates for {}:", symbol);
                futureGannDates.forEach(gann ->
                        log.info("   {}: {} from {} (${}) on {}",
                                gann.getDate(),
                                gann.getType(),
                                gann.getSourcePivot().getType(),
                                String.format("%,.0f", gann.getSourcePivot().getPrice()),
                                gann.getSourcePivot().getDate()));
            }

            return ResponseEntity.ok(futureGannDates);

        } catch (Exception e) {
            log.error("❌ Failed to get Gann dates for {}: {}", symbol, e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to get Gann dates", "message", e.getMessage()));
        }
    }

    /**
     * Get major cycle pivots (copy from TimeGeometryService)
     */
    private List<PricePivot> getMajorCyclePivots(String symbol, List<BinanceHistoricalService.OHLCData> monthlyData) {
        List<PricePivot> pivots = new ArrayList<>();

        // For BTC: Use known major pivots
        if (symbol.equals("BTC")) {
            pivots.add(new PricePivot(LocalDate.of(2018, 12, 15), 3100.0, "MAJOR_LOW", 1.0));
            pivots.add(new PricePivot(LocalDate.of(2023, 1, 1), 15455.0, "MAJOR_LOW", 0.9));
            pivots.add(new PricePivot(LocalDate.of(2024, 3, 1), 72000.0, "MAJOR_HIGH", 0.8));
            pivots.add(new PricePivot(LocalDate.of(2025, 10, 1), 126272.76, "MAJOR_HIGH", 1.0));

            log.info("💰 BTC: Using 4 major cycle pivots for Gann dates");
            return pivots;
        }

        // For other symbols: Find 2-4 most significant pivots
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

            // Try to find one more significant pivot (second highest or second lowest)
            if (monthlyData.size() >= 48) { // 4+ years of data
                // Find second highest (excluding the highest we already have)
                BinanceHistoricalService.OHLCData secondHighest = monthlyData.stream()
                        .filter(data -> data != highest)
                        .max(Comparator.comparingDouble(BinanceHistoricalService.OHLCData::high))
                        .orElse(null);

                if (secondHighest != null && secondHighest.high() > highest.high() * 0.7) {
                    // Only add if it's significant (at least 70% of the highest)
                    pivots.add(new PricePivot(
                            convertTimestampToDate(secondHighest.timestamp()),
                            secondHighest.high(),
                            "MAJOR_HIGH",
                            0.8
                    ));
                }
            }
        }

        log.info("📊 {}: Found {} major pivots for Gann dates", symbol, pivots.size());
        return pivots;
    }

    /**
     * Generate Gann dates from major pivots
     */
    private List<GannDate> generateGannDatesFromMajorPivots(List<PricePivot> majorPivots) {
        List<GannDate> gannDates = new ArrayList<>();

        for (PricePivot pivot : majorPivots) {
            for (int period : GANN_PERIODS) {
                LocalDate gannDate = pivot.getDate().plusDays(period);

                // Only include dates from last 5 years (to avoid ancient pivots)
                LocalDate fiveYearsAgo = LocalDate.now().minusYears(5);
                if (pivot.getDate().isAfter(fiveYearsAgo)) {
                    // Limit future projections to reasonable timeframe
                    LocalDate maxFutureDate = LocalDate.now().plusYears(3);
                    if (gannDate.isBefore(maxFutureDate)) {
                        GannDate gann = new GannDate(
                                gannDate,
                                period + "D_ANNIVERSARY",
                                pivot
                        );
                        gannDates.add(gann);
                    }
                }
            }
        }

        // Sort by date
        gannDates.sort(Comparator.comparing(GannDate::getDate));

        log.info("Generated {} Gann dates from {} major pivots", gannDates.size(), majorPivots.size());
        return gannDates;
    }

    /**
     * Helper method to convert timestamp
     */
    private LocalDate convertTimestampToDate(long timestamp) {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    /**
     * Find significant pivot points (replicating BacktestService logic)
     */
    private List<LocalDate> findSignificantPivots(List<BinanceHistoricalService.OHLCData> historicalData) {
        List<LocalDate> pivots = new ArrayList<>();

        if (historicalData.size() < PIVOT_LOOKBACK_DAYS * 2) {
            return pivots;
        }

        for (int i = PIVOT_LOOKBACK_DAYS; i < historicalData.size() - PIVOT_LOOKBACK_DAYS; i++) {
            boolean isHigh = true;
            boolean isLow = true;
            double currentHigh = historicalData.get(i).high();
            double currentLow = historicalData.get(i).low();

            // Check surrounding window
            for (int j = i - PIVOT_LOOKBACK_DAYS; j <= i + PIVOT_LOOKBACK_DAYS; j++) {
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

        log.debug("Found {} significant pivot points", pivots.size());
        return pivots;
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
     * Generate Gann dates from pivot points
     */
    private List<GannDate> generateGannDatesFromPivots(List<PricePivot> pivots) {
        List<GannDate> gannDates = new ArrayList<>();

        for (PricePivot pivot : pivots) {
            for (int period : GANN_PERIODS) {
                LocalDate gannDate = pivot.getDate().plusDays(period);

                // Create Gann date
                GannDate gann = new GannDate(
                        gannDate,
                        period + "D_ANNIVERSARY",
                        pivot
                );

                gannDates.add(gann);
            }
        }

        // Sort by date
        gannDates.sort(Comparator.comparing(GannDate::getDate));

        return gannDates;
    }

    /**
     * Get Gann dates with confluence (multiple signals on same date)
     */
    @GetMapping("/confluence/{symbol}")
    public ResponseEntity<?> getGannConfluenceDates(@PathVariable String symbol) {
        try {
            log.info("🎯 Fetching Gann confluence dates for {}", symbol);

            // Get all Gann dates
            List<GannDate> allGannDates = getGannDatesInternal(symbol);

            if (allGannDates.isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            // Group by date to find confluence
            Map<LocalDate, List<GannDate>> datesMap = new HashMap<>();
            for (GannDate gann : allGannDates) {
                datesMap.computeIfAbsent(gann.getDate(), k -> new ArrayList<>())
                        .add(gann);
            }

            // Find dates with multiple Gann signals (confluence)
            List<Map<String, Object>> confluenceDates = new ArrayList<>();
            for (Map.Entry<LocalDate, List<GannDate>> entry : datesMap.entrySet()) {
                if (entry.getValue().size() >= 2) { // At least 2 Gann signals
                    Map<String, Object> confluence = new HashMap<>();
                    confluence.put("date", entry.getKey());
                    confluence.put("count", entry.getValue().size());
                    confluence.put("signals", entry.getValue().stream()
                            .map(g -> Map.of(
                                    "type", g.getType(),
                                    "sourcePrice", g.getSourcePivot().getPrice(),
                                    "sourceType", g.getSourcePivot().getType(),
                                    "sourceDate", g.getSourcePivot().getDate()
                            ))
                            .collect(Collectors.toList()));
                    confluence.put("intensity", Math.min(0.95, entry.getValue().size() * 0.3));

                    confluenceDates.add(confluence);
                }
            }

            // Sort by date and filter to future
            LocalDate today = LocalDate.now();
            List<Map<String, Object>> futureConfluence = confluenceDates.stream()
                    .filter(c -> !((LocalDate) c.get("date")).isBefore(today))
                    .sorted(Comparator.comparing(c -> (LocalDate) c.get("date")))
                    .limit(10) // Limit results
                    .collect(Collectors.toList());

            log.info("✅ Found {} Gann confluence dates for {}", futureConfluence.size(), symbol);
            return ResponseEntity.ok(futureConfluence);

        } catch (Exception e) {
            log.error("❌ Failed to get Gann confluence dates for {}: {}", symbol, e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to get Gann confluence dates", "message", e.getMessage()));
        }
    }

    /**
     * Internal method to get Gann dates
     */
    private List<GannDate> getGannDatesInternal(String symbol) {
        List<BinanceHistoricalService.OHLCData> historicalData =
                binanceHistoricalService.getHistoricalData(symbol);

        if (historicalData == null || historicalData.isEmpty()) {
            return Collections.emptyList();
        }

        List<LocalDate> pivotDates = findSignificantPivots(historicalData);
        List<PricePivot> pricePivots = convertToPricePivots(historicalData, pivotDates);
        return generateGannDatesFromPivots(pricePivots);
    }

    @GetMapping("/debug/analysis/{symbol}")
    public ResponseEntity<?> debugAnalysis(@PathVariable String symbol) {
        try {
            VortexAnalysis analysis = timeGeometryService.analyzeSymbol(symbol);

            Map<String, Object> debug = new HashMap<>();
            debug.put("symbol", symbol);
            debug.put("hasHistoricalData", analysis != null);

            if (analysis != null) {
                debug.put("timeProjections", analysis.getFibonacciTimeProjections() != null ?
                        analysis.getFibonacciTimeProjections().size() : 0);
                debug.put("priceLevels", analysis.getFibonacciPriceLevels() != null ?
                        analysis.getFibonacciPriceLevels().size() : 0);
                debug.put("gannDates", analysis.getGannDates() != null ?
                        analysis.getGannDates().size() : 0);
                debug.put("vortexWindows", analysis.getVortexWindows() != null ?
                        analysis.getVortexWindows().size() : 0);

                // Show first few Gann dates
                if (analysis.getGannDates() != null && !analysis.getGannDates().isEmpty()) {
                    List<Map<String, Object>> sampleGann = new ArrayList<>();

                    for (int i = 0; i < Math.min(3, analysis.getGannDates().size()); i++) {
                        GannDate g = analysis.getGannDates().get(i);
                        Map<String, Object> gannMap = new HashMap<>();
                        gannMap.put("date", g.getDate());
                        gannMap.put("type", g.getType());
                        gannMap.put("sourceDate", g.getSourcePivot().getDate());
                        gannMap.put("sourcePrice", g.getSourcePivot().getPrice());
                        sampleGann.add(gannMap);
                    }

                    debug.put("sampleGannDates", sampleGann);
                }
            }

            return ResponseEntity.ok(debug);

        } catch (Exception e) {
            log.error("Debug analysis failed for {}: {}", symbol, e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of(
                            "error", "Debug analysis failed",
                            "message", e.getMessage()
                    ));
        }
    }

    @GetMapping("/dates/{symbol}/filtered")
    public ResponseEntity<?> getFilteredGannDates(
            @PathVariable String symbol,
            @RequestParam(required = false, defaultValue = "STANDARD") String level,
            @RequestParam(required = false, defaultValue = "50") int limit) {

        try {
            log.info("📅 Fetching filtered Gann dates for {} at {} level (limit: {})", symbol, level, limit);

            // Define comprehensive Gann periods
            int[] comprehensivePeriods = {
                    30, 45, 60, 72, 90, 120, 135, 144, 150, 180, 216, 225,
                    240, 270, 288, 300, 315, 330, 360, 49, 98, 147, 196,
                    540, 720, 900, 1080, 1260, 1440
            };

            // Define periods based on level
            int[] periods;
            switch(level.toUpperCase()) {
                case "BASIC":
                    periods = new int[]{90, 180, 360};
                    log.info("Using BASIC Gann periods: 90, 180, 360 days");
                    break;
                case "ADVANCED":
                    periods = new int[]{30, 45, 60, 90, 120, 135, 144, 180, 225, 270, 315, 360, 540, 720, 1080, 1440};
                    log.info("Using ADVANCED Gann periods (16 periods)");
                    break;
                case "COMPREHENSIVE":
                    periods = comprehensivePeriods;
                    log.info("Using COMPREHENSIVE Gann periods ({} periods)", periods.length);
                    break;
                default: // STANDARD
                    periods = new int[]{30, 45, 60, 90, 120, 135, 144, 180, 225, 270, 315, 360, 540, 720};
                    log.info("Using STANDARD Gann periods (14 periods)");
            }

            // Get monthly data for major cycle detection
            List<BinanceHistoricalService.OHLCData> monthlyData =
                    binanceHistoricalService.getMonthlyData(symbol);

            if (monthlyData == null || monthlyData.isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            // Get ONLY MAJOR cycle pivots
            List<PricePivot> majorPivots = getMajorCyclePivots(symbol, monthlyData);

            if (majorPivots.isEmpty()) {
                log.warn("⚠️ No major pivots found for {}", symbol);
                return ResponseEntity.ok(Collections.emptyList());
            }

            // Generate Gann dates ONLY from these major pivots
            List<GannDate> allGannDates = new ArrayList<>();

            for (PricePivot pivot : majorPivots) {
                for (int period : periods) {
                    LocalDate gannDate = pivot.getDate().plusDays(period);

                    // Only include dates from recent pivots (last 5 years)
                    LocalDate fiveYearsAgo = LocalDate.now().minusYears(5);
                    if (pivot.getDate().isAfter(fiveYearsAgo)) {
                        // Limit to reasonable future (next 3 years max)
                        LocalDate maxFutureDate = LocalDate.now().plusYears(3);
                        if (!gannDate.isAfter(maxFutureDate)) {
                            GannDate gann = new GannDate(
                                    gannDate,
                                    period + "D_ANNIVERSARY",
                                    pivot
                            );
                            allGannDates.add(gann);
                        }
                    }
                }
            }

            // Filter to future dates and sort
            LocalDate today = LocalDate.now();
            List<GannDate> futureGannDates = allGannDates.stream()
                    .filter(gann -> !gann.getDate().isBefore(today))
                    .sorted(Comparator.comparing(GannDate::getDate))
                    .limit(limit) // Use parameterized limit
                    .collect(Collectors.toList());

            log.info("✅ Generated {} Gann dates for {} ({} future, level: {})",
                    allGannDates.size(), symbol, futureGannDates.size(), level);

            // Log summary
            if (!futureGannDates.isEmpty()) {
                log.info("📅 Next 5 Gann Dates for {} ({} level):", symbol, level);
                futureGannDates.stream()
                        .limit(5)
                        .forEach(gann -> log.info("   {}: {} from {} (${})",
                                gann.getDate(),
                                gann.getType(),
                                gann.getSourcePivot().getDate(),
                                String.format("%,.0f", gann.getSourcePivot().getPrice())));
            }

            return ResponseEntity.ok(futureGannDates);

        } catch (Exception e) {
            log.error("❌ Failed to get filtered Gann dates for {}: {}", symbol, e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to get filtered Gann dates", "message", e.getMessage()));
        }
    }

}