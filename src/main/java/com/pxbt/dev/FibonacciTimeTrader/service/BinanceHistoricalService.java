package com.pxbt.dev.FibonacciTimeTrader.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pxbt.dev.FibonacciTimeTrader.Gateway.BinanceGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class BinanceHistoricalService {

    private final BinanceGateway binanceGateway;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record OHLCData(long timestamp, double open, double high, double low, double close, double volume) {}

    private final Map<String, List<OHLCData>> currentData = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("📚 BinanceHistoricalService initializing for MAJOR CYCLE Time Geometry...");
        loadExtendedHistoricalData();
    }

    private void loadExtendedHistoricalData() {
        try {
            Map<String, List<OHLCData>> allData = new HashMap<>();
            String[] symbols = {"BTC", "SOL", "TAO", "WIF"};

            for (String symbol : symbols) {
                // ✅ GET 5+ YEARS OF DATA USING MULTIPLE CALLS
                List<OHLCData> extendedData = fetchExtendedHistoricalData(symbol);
                allData.put(symbol, extendedData);

                // Log the full date range
                if (!extendedData.isEmpty()) {
                    LocalDate start = convertTimestampToDate(extendedData.get(0).timestamp());
                    LocalDate end = convertTimestampToDate(extendedData.get(extendedData.size()-1).timestamp());
                    long days = java.time.temporal.ChronoUnit.DAYS.between(start, end);
                    double years = days / 365.0;

                    // ✅ FIXED: Format the years before logging
                    String formattedYears = String.format("%.1f", years);

                    log.info("📊 {}: Loaded {} OHLC data points ({} to {}) - {} years",
                            symbol, extendedData.size(), start, end, formattedYears);
                }
            }

            currentData.putAll(allData);
            log.info("✅ Historical data loaded for {} symbols", symbols.length);

        } catch (Exception e) {
            log.error("❌ Failed to load extended historical data: {}", e.getMessage());
            // Initialize empty data
            for (String symbol : new String[]{"BTC", "SOL", "TAO", "WIF"}) {
                currentData.put(symbol, new ArrayList<>());
            }
        }
    }

    /**
     * ✅ ENHANCED: Fetch 5+ years of historical data using multiple API calls
     */
    /**
     * ✅ ENHANCED: Fetch 5+ years of historical data using multiple API calls
     */
    private List<OHLCData> fetchExtendedHistoricalData(String symbol) {
        List<OHLCData> allData = new ArrayList<>();

        try {
            log.info("🔍 Fetching 5+ YEARS of historical data for {}...", symbol);

            // For BTC specifically, we need data back to 2018
            if (symbol.equals("BTC")) {
                log.info("💰 BTC: Fetching data back to 2018 for major cycle detection...");

                // Strategy for BTC: Get 100 monthly points (over 8 years)
                List<OHLCData> monthlyData = fetchBinanceData(symbol, "1M", 100); // 8+ years

                // Also get weekly for medium-term
                List<OHLCData> weeklyData = fetchExtendedDataWithMultipleCalls(symbol, "1w", 400); // 7+ years

                // Daily for recent
                List<OHLCData> dailyData = fetchExtendedDataWithMultipleCalls(symbol, "1d", 730); // 2 years

                // Combine all
                Set<Long> seenTimestamps = new HashSet<>();

                for (OHLCData data : dailyData) {
                    if (seenTimestamps.add(data.timestamp())) {
                        allData.add(data);
                    }
                }

                for (OHLCData data : weeklyData) {
                    if (seenTimestamps.add(data.timestamp())) {
                        allData.add(data);
                    }
                }

                for (OHLCData data : monthlyData) {
                    if (seenTimestamps.add(data.timestamp())) {
                        allData.add(data);
                    }
                }

            } else {
                // For other symbols, use original strategy
                List<OHLCData> monthlyData = fetchBinanceData(symbol, "1M", 84);
                List<OHLCData> weeklyData = fetchExtendedDataWithMultipleCalls(symbol, "1w", 260);
                List<OHLCData> dailyData = fetchExtendedDataWithMultipleCalls(symbol, "1d", 730);

                // Combine
                Set<Long> seenTimestamps = new HashSet<>();

                for (OHLCData data : dailyData) {
                    if (seenTimestamps.add(data.timestamp())) {
                        allData.add(data);
                    }
                }

                for (OHLCData data : weeklyData) {
                    if (seenTimestamps.add(data.timestamp())) {
                        allData.add(data);
                    }
                }

                for (OHLCData data : monthlyData) {
                    if (seenTimestamps.add(data.timestamp())) {
                        allData.add(data);
                    }
                }
            }

            // Sort by timestamp (oldest first)
            allData.sort(Comparator.comparing(OHLCData::timestamp));

            // Log the date range
            if (!allData.isEmpty()) {
                LocalDate start = convertTimestampToDate(allData.get(0).timestamp());
                LocalDate end = convertTimestampToDate(allData.get(allData.size()-1).timestamp());
                long days = java.time.temporal.ChronoUnit.DAYS.between(start, end);
                double years = days / 365.0;

                log.info("✅ {}: {} total data points from {} to {} {} years)",
                        symbol, allData.size(), start, end, years);

                if (symbol.equals("BTC") && years < 7.0) {
                    log.warn("⚠️ BTC: Only {} years of data - may miss 2018 bear market low", years);
                }
            }

        } catch (Exception e) {
            log.error("❌ Extended data fetch failed for {}: {}", symbol, e.getMessage());

            // Fallback
            try {
                List<OHLCData> weeklyData = fetchBinanceData(symbol, "1w", 260);
                allData.addAll(weeklyData);
                allData.sort(Comparator.comparing(OHLCData::timestamp));
                log.info("✅ {}: Loaded {} weekly data points as fallback", symbol, weeklyData.size());
            } catch (Exception ex) {
                log.error("❌ Fallback also failed for {}: {}", symbol, ex.getMessage());
            }
        }

        return allData;
    }

    /**
     * ✅ FIXED: Fetch data with multiple calls for maximum history
     * Actually USES this method now
     */
    private List<OHLCData> fetchExtendedDataWithMultipleCalls(String symbol, String interval, int totalPoints) {
        List<OHLCData> allData = new ArrayList<>();
        int maxLimitPerCall = 1000; // Binance max per call

        if (totalPoints <= maxLimitPerCall) {
            // Single call is enough
            return fetchBinanceData(symbol, interval, totalPoints);
        }

        int callsNeeded = (int) Math.ceil((double) totalPoints / maxLimitPerCall);

        log.info("🔄 Fetching {} {} data points for {} in {} calls",
                totalPoints, interval, symbol, callsNeeded);

        try {
            for (int call = 0; call < callsNeeded; call++) {
                int limit = Math.min(maxLimitPerCall, totalPoints - (call * maxLimitPerCall));
                if (limit <= 0) break;

                log.debug("📞 Call {}/{}: fetching {} {} points",
                        call + 1, callsNeeded, limit, interval);

                List<OHLCData> chunk = fetchBinanceData(symbol, interval, limit);
                if (chunk.isEmpty()) {
                    log.warn("⚠️ Empty response for {} {} on call {}/{}",
                            symbol, interval, call + 1, callsNeeded);
                    break;
                }

                allData.addAll(chunk);

                // Small delay to avoid rate limiting
                if (call < callsNeeded - 1) {
                    Thread.sleep(300);
                }
            }

            // Sort by timestamp
            allData.sort(Comparator.comparing(OHLCData::timestamp));

            log.info("✅ {}: Fetched {} {} data points via {} calls",
                    symbol, allData.size(), interval, callsNeeded);

        } catch (Exception e) {
            log.error("❌ Multi-call fetch failed for {} {}: {}", symbol, interval, e.getMessage());
        }

        return allData;
    }

    /**
     * FOR TimeGeometryService - returns OHLC data for pivot detection
     */
    public List<OHLCData> getHistoricalData(String symbol) {
        List<OHLCData> data = currentData.getOrDefault(symbol, new ArrayList<>());

        // Log data availability
        if (!data.isEmpty()) {
            LocalDate start = convertTimestampToDate(data.get(0).timestamp());
            LocalDate end = convertTimestampToDate(data.get(data.size()-1).timestamp());
            long days = java.time.temporal.ChronoUnit.DAYS.between(start, end);

            log.debug("📊 {}: Returning {} data points ({} to {}, {:.1f} years)",
                    symbol, data.size(), start, end, days / 365.0);
        }

        return new ArrayList<>(data);
    }

    /**
     * Get monthly data for major cycle detection
     */
    public List<OHLCData> getMonthlyData(String symbol) {
        List<OHLCData> allData = getHistoricalData(symbol);
        if (allData.isEmpty()) return allData;

        // Extract approximate monthly data (every 30th point or by actual month)
        List<OHLCData> monthlyData = new ArrayList<>();
        LocalDate lastMonth = null;

        for (OHLCData data : allData) {
            LocalDate currentDate = convertTimestampToDate(data.timestamp());

            if (lastMonth == null ||
                    currentDate.getMonth() != lastMonth.getMonth() ||
                    currentDate.getYear() != lastMonth.getYear()) {

                monthlyData.add(data);
                lastMonth = currentDate;
            }
        }

        log.debug("📅 {}: Extracted {} monthly data points", symbol, monthlyData.size());
        return monthlyData;
    }

    /**
     * Get weekly data for major cycle detection
     */
    public List<OHLCData> getWeeklyData(String symbol) {
        List<OHLCData> allData = getHistoricalData(symbol);
        if (allData.isEmpty()) return allData;

        // Extract approximate weekly data (every 7th point or by actual week)
        List<OHLCData> weeklyData = new ArrayList<>();
        int weekCounter = 0;

        for (int i = 0; i < allData.size(); i++) {
            if (i % 7 == 0) {
                weeklyData.add(allData.get(i));
                weekCounter++;
            }
        }

        log.debug("📅 {}: Extracted {} weekly data points", symbol, weeklyData.size());
        return weeklyData;
    }

    /**
     * Helper method for single API call
     */
    private List<OHLCData> fetchBinanceData(String symbol, String timeframe, int limit) {
        try {
            String binanceInterval = convertTimeframeToBinanceInterval(timeframe);

            log.debug("📡 Fetching {} {} data for {} (limit: {})",
                    symbol, timeframe, binanceInterval, limit);

            String response = binanceGateway.getRawKlines(symbol, binanceInterval, limit)
                    .blockOptional()
                    .orElse("[]");

            if (response == null || response.trim().isEmpty() || response.equals("[]")) {
                log.warn("⚠️ Empty response for {} {}", symbol, timeframe);
                return new ArrayList<>();
            }

            return parseBinanceKlinesToOHLC(response);

        } catch (Exception e) {
            log.error("❌ Failed to fetch Binance data for {} {}: {}",
                    symbol, timeframe, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Parsing method that returns simple OHLC data
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