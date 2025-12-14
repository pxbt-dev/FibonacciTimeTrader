package com.pxbt.dev.FibonacciTimeTrader.controller;

import com.pxbt.dev.FibonacciTimeTrader.model.GannDate;
import com.pxbt.dev.FibonacciTimeTrader.model.PricePivot;
import com.pxbt.dev.FibonacciTimeTrader.service.BinanceHistoricalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/gann")
public class GannDateController {

    @Autowired
    private BinanceHistoricalService binanceHistoricalService;

    private static final int[] GANN_PERIODS = {90, 180, 360};
    private static final int PIVOT_LOOKBACK_DAYS = 10;

    /**
     * Get pure Gann dates for a symbol
     */
    @GetMapping("/dates/{symbol}")
    public ResponseEntity<?> getGannDates(@PathVariable String symbol) {
        try {
            log.info("📅 Fetching Gann dates for {}", symbol);

            // Get historical data
            List<BinanceHistoricalService.OHLCData> historicalData =
                    binanceHistoricalService.getHistoricalData(symbol);

            if (historicalData == null || historicalData.isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            // Find significant pivot points (reusing logic from BacktestService)
            List<LocalDate> pivotDates = findSignificantPivots(historicalData);

            // Convert to PricePivot objects
            List<PricePivot> pricePivots = convertToPricePivots(historicalData, pivotDates);

            // Generate Gann dates from pivots
            List<GannDate> gannDates = generateGannDatesFromPivots(pricePivots);

            // Filter to future dates only
            LocalDate today = LocalDate.now();
            List<GannDate> futureGannDates = gannDates.stream()
                    .filter(gann -> !gann.getDate().isBefore(today))
                    .sorted(Comparator.comparing(GannDate::getDate))
                    .limit(20) // Limit to 20 future dates for UI
                    .collect(Collectors.toList());

            log.info("✅ Found {} Gann dates for {} ({} future)",
                    gannDates.size(), symbol, futureGannDates.size());

            return ResponseEntity.ok(futureGannDates);

        } catch (Exception e) {
            log.error("❌ Failed to get Gann dates for {}: {}", symbol, e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to get Gann dates", "message", e.getMessage()));
        }
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
}