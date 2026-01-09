package com.pxbt.dev.FibonacciTimeTrader.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.pxbt.dev.FibonacciTimeTrader.Gateway.BinanceGateway;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BinanceHistoricalService {

    @Autowired
    private HistoricalDataFileService fileService;

    private final BinanceGateway binanceGateway;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record OHLCData(long timestamp, double open, double high, double low, double close, double volume) {}

    // Small cache for recent/active data only
    private final Cache<String, List<OHLCData>> recentDataCache = Caffeine.newBuilder()
            .maximumSize(20) // Cache 20 symbol/timeframe combos
            .expireAfterWrite(30, TimeUnit.MINUTES) // Expire after 30 minutes
            .build();

    @PostConstruct
    public void init() {
        log.info("📚 BinanceHistoricalService ready - loading data on demand");
        // No data loaded upfront - load only when requested
    }

    /**
     * Main method: Get historical data with load-on-demand
     */
    public List<OHLCData> getHistoricalData(String symbol) {
        String cacheKey = symbol + "_1d";

        return recentDataCache.get(cacheKey, key -> {
            log.info("📊 Loading historical data for {} (on-demand)", symbol);

            // 1. Try to load from file first (fast, persistent)
            List<OHLCData> fileData = fileService.loadHistoricalData(symbol, "1d");

            // 2. If file is recent enough (< 24 hours old), use it
            if (!fileData.isEmpty() && !fileService.needsUpdate(symbol, "1d", 24)) {
                log.info("✅ Loaded {} daily candles for {} from file", fileData.size(), symbol);
                return fileData;
            }

            // 3. File is old or missing - fetch from API
            log.info("🔄 File outdated/missing - fetching fresh data for {} from Binance", symbol);
            List<OHLCData> apiData = fetchOptimizedData(symbol, "1d", 730); // 2 years

            // 4. Save to file for next time
            fileService.saveHistoricalData(symbol, "1d", apiData);

            log.info("✅ Fetched {} daily candles for {} from API", apiData.size(), symbol);
            return apiData;
        });
    }

    /**
     * Get monthly data for major cycle detection
     */
    public List<OHLCData> getMonthlyData(String symbol) {
        String cacheKey = symbol + "_1M";

        return recentDataCache.get(cacheKey, key -> {
            // 1. Try file first
            List<OHLCData> fileData = fileService.loadHistoricalData(symbol, "1M");

            // 2. Monthly data updates weekly (not daily)
            if (!fileData.isEmpty() && !fileService.needsUpdate(symbol, "1M", 24 * 7)) {
                return fileData;
            }

            // 3. Fetch monthly data directly (more efficient than converting daily)
            log.info("📅 Fetching monthly data for {}", symbol);
            List<OHLCData> monthlyData = fetchBinanceData(symbol, "1M", 84); // 7 years

            // 4. Save for next time
            fileService.saveHistoricalData(symbol, "1M", monthlyData);

            return monthlyData;
        });
    }

    /**
     * Get weekly data
     */
    public List<OHLCData> getWeeklyData(String symbol) {
        String cacheKey = symbol + "_1w";

        return recentDataCache.get(cacheKey, key -> {
            List<OHLCData> fileData = fileService.loadHistoricalData(symbol, "1w");

            if (!fileData.isEmpty() && !fileService.needsUpdate(symbol, "1w", 24 * 3)) { // Update every 3 days
                return fileData;
            }

            log.info("📅 Fetching weekly data for {}", symbol);
            List<OHLCData> weeklyData = fetchBinanceData(symbol, "1w", 260); // 5 years

            fileService.saveHistoricalData(symbol, "1w", weeklyData);

            return weeklyData;
        });
    }

    /**
     * Optimized data fetching with rate limiting
     */
    private List<OHLCData> fetchOptimizedData(String symbol, String interval, int totalPoints) {
        // For ML/analysis, we want consistent data
        // Fetch in one go if possible, with proper rate limiting

        try {
            log.info("📡 Fetching {} {} candles for {}", totalPoints, interval, symbol);

            // Add rate limiting delay
            Thread.sleep(1000); // Be nice to Binance API

            // Fetch all at once (max 1000 per call for Binance)
            int pointsToFetch = Math.min(totalPoints, 1000);
            List<OHLCData> data = fetchBinanceData(symbol, interval, pointsToFetch);

            // If we need more than 1000 points, do multiple calls
            if (totalPoints > 1000 && !data.isEmpty()) {
                int additionalCalls = (int) Math.ceil((totalPoints - 1000) / 1000.0);

                for (int i = 1; i <= additionalCalls; i++) {
                    Thread.sleep(1000); // Rate limit between calls

                    // Fetch next chunk
                    List<OHLCData> chunk = fetchBinanceData(symbol, interval, 1000);
                    if (chunk.isEmpty()) break;

                    data.addAll(chunk);

                    log.debug("🔄 Additional fetch {}/{}: {} candles",
                            i, additionalCalls, chunk.size());
                }
            }

            // Sort by timestamp
            data.sort(Comparator.comparing(OHLCData::timestamp));

            log.info("✅ Fetched {} {} candles for {}", data.size(), interval, symbol);
            return data;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ Fetch interrupted for {} {}", symbol, interval);
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("❌ Failed to fetch data for {} {}: {}", symbol, interval, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Single Binance API call
     */
    private List<OHLCData> fetchBinanceData(String symbol, String timeframe, int limit) {
        try {
            String binanceInterval = convertTimeframeToBinanceInterval(timeframe);

            log.debug("📡 API call: {} {} (limit: {})", symbol, binanceInterval, limit);

            String response = binanceGateway.getRawKlines(symbol, binanceInterval, limit)
                    .blockOptional()
                    .orElse("[]");

            if (response == null || response.trim().isEmpty() || response.equals("[]")) {
                log.warn("⚠️ Empty API response for {} {}", symbol, timeframe);
                return new ArrayList<>();
            }

            List<OHLCData> data = parseBinanceKlinesToOHLC(response);
            log.debug("📡 Got {} candles for {} {}", data.size(), symbol, timeframe);

            return data;

        } catch (Exception e) {
            log.error("❌ API call failed for {} {}: {}", symbol, timeframe, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Background update for ML retraining
     * Called by scheduler, not blocking user requests
     */
    public void backgroundUpdate(String symbol) {
        try {
            log.info("🔄 Background update for {} ML data", symbol);

            // Fetch only recent data (last 30 days)
            List<OHLCData> newData = fetchBinanceData(symbol, "1d", 30);

            if (newData.isEmpty()) {
                log.warn("⚠️ No new data fetched for {}", symbol);
                return;
            }

            // Load existing data
            List<OHLCData> existing = fileService.loadHistoricalData(symbol, "1d");

            // Merge: keep existing, add new, remove duplicates
            List<OHLCData> merged = mergeData(existing, newData);

            // Save updated dataset
            fileService.saveHistoricalData(symbol, "1d", merged);

            // Invalidate cache so next request gets fresh data
            recentDataCache.invalidate(symbol + "_1d");

            log.info("✅ Background update: {} now has {} candles", symbol, merged.size());

        } catch (Exception e) {
            log.error("❌ Background update failed for {}: {}", symbol, e.getMessage());
            // Don't throw - background updates should fail silently
        }
    }

    /**
     * Merge new data with existing, remove duplicates
     */
    private List<OHLCData> mergeData(List<OHLCData> existing, List<OHLCData> newData) {
        if (existing.isEmpty()) return newData;
        if (newData.isEmpty()) return existing;

        // Use map to remove duplicates by timestamp
        Map<Long, OHLCData> mergedMap = new TreeMap<>();

        // Add existing data
        for (OHLCData data : existing) {
            mergedMap.put(data.timestamp(), data);
        }

        // Add/overwrite with new data (newer data wins)
        for (OHLCData data : newData) {
            mergedMap.put(data.timestamp(), data);
        }

        // Convert back to sorted list
        List<OHLCData> merged = new ArrayList<>(mergedMap.values());
        merged.sort(Comparator.comparing(OHLCData::timestamp));

        return merged;
    }

    /**
     * Parse Binance API response
     */
    private List<OHLCData> parseBinanceKlinesToOHLC(String response) {
        try {
            if (response == null || response.trim().isEmpty() || response.equals("[]")) {
                return new ArrayList<>();
            }

            List<List<Object>> klines = objectMapper.readValue(response, new TypeReference<>() {});
            List<OHLCData> ohlcData = new ArrayList<>();

            for (List<Object> kline : klines) {
                OHLCData data = new OHLCData(
                        Long.parseLong(kline.get(0).toString()),
                        Double.parseDouble(kline.get(1).toString()),
                        Double.parseDouble(kline.get(2).toString()),
                        Double.parseDouble(kline.get(3).toString()),
                        Double.parseDouble(kline.get(4).toString()),
                        Double.parseDouble(kline.get(5).toString())
                );
                ohlcData.add(data);
            }

            return ohlcData;
        } catch (Exception e) {
            log.error("❌ Failed to parse Binance response: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String convertTimeframeToBinanceInterval(String timeframe) {
        return switch(timeframe) {
            case "1d" -> "1d";
            case "1w" -> "1w";
            case "1M" -> "1M";
            default -> "1d";
        };
    }

    private LocalDate convertTimestampToDate(long timestamp) {
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate();
    }
}