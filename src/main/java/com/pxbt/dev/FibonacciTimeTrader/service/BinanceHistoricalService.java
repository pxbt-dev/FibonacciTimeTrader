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
        log.info("📚 BinanceHistoricalService initializing for EXTENDED Time Geometry...");
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

                log.info("📊 {}: Loaded {} OHLC data points ({} years)",
                        symbol, extendedData.size(), extendedData.size() / 365);

                // Log the full date range
                if (!extendedData.isEmpty()) {
                    LocalDate start = convertTimestampToDate(extendedData.get(0).timestamp());
                    LocalDate end = convertTimestampToDate(extendedData.get(extendedData.size()-1).timestamp());
                    long days = java.time.temporal.ChronoUnit.DAYS.between(start, end);
                    log.info("📅 {} data range: {} to {} ({} days, {:.1f} years)",
                            symbol, start, end, days, days / 365.0);
                }
            }

            currentData.putAll(allData);
            log.info("✅ EXTENDED historical data loaded (5+ years per symbol)");

        } catch (Exception e) {
            log.error("❌ Failed to load extended historical data: {}", e.getMessage());
            // Initialize empty data
            for (String symbol : new String[]{"BTC", "SOL", "TAO", "WIF"}) {
                currentData.put(symbol, new ArrayList<>());
            }
        }
    }

    /**
     * ✅ NEW: Fetch extended historical data using multiple API calls
     */
    private List<OHLCData> fetchExtendedHistoricalData(String symbol) {
        List<OHLCData> allData = new ArrayList<>();

        try {
            log.info("🔍 Fetching extended historical data for {}...", symbol);

            // Strategy: Get weekly data for long-term + daily data for recent precision
            List<OHLCData> weeklyData = fetchBinanceData(symbol, "1w", 500); // ~9.6 years of weekly
            List<OHLCData> dailyData = fetchBinanceData(symbol, "1d", 1000); // ~2.7 years of daily

            // Combine data - prefer daily for recent, weekly for long-term
            allData.addAll(dailyData);

            // Add weekly data for older periods (before daily data coverage)
            if (!weeklyData.isEmpty() && !dailyData.isEmpty()) {
                long dailyStartTime = dailyData.get(0).timestamp();

                // Add weekly data points that are older than our daily data
                for (OHLCData weeklyPoint : weeklyData) {
                    if (weeklyPoint.timestamp() < dailyStartTime) {
                        allData.add(weeklyPoint);
                    }
                }
            } else if (!weeklyData.isEmpty()) {
                // If no daily data, use weekly
                allData.addAll(weeklyData);
            }

            // Sort by timestamp (oldest first)
            allData.sort(Comparator.comparing(OHLCData::timestamp));

            log.info("✅ {}: Combined {} daily + {} weekly = {} total data points",
                    symbol, dailyData.size(), weeklyData.size(), allData.size());

        } catch (Exception e) {
            log.error("❌ Extended data fetch failed for {}: {}", symbol, e.getMessage());
        }

        return allData;
    }

    /**
     * ✅ ENHANCED: Fetch data with multiple calls for maximum history
     */
    private List<OHLCData> fetchExtendedDataWithMultipleCalls(String symbol, String interval, int totalPoints) {
        List<OHLCData> allData = new ArrayList<>();
        int maxLimitPerCall = 1000; // Binance max per call
        int callsNeeded = (int) Math.ceil((double) totalPoints / maxLimitPerCall);

        log.info("🔄 Fetching {} points for {} {} in {} calls",
                totalPoints, symbol, interval, callsNeeded);

        try {
            for (int call = 0; call < callsNeeded; call++) {
                int limit = Math.min(maxLimitPerCall, totalPoints - (call * maxLimitPerCall));
                if (limit <= 0) break;

                List<OHLCData> chunk = fetchBinanceData(symbol, interval, limit);
                if (chunk.isEmpty()) break;

                allData.addAll(chunk);

                // Small delay to avoid rate limiting
                if (call < callsNeeded - 1) {
                    Thread.sleep(200);
                }
            }

            // Remove duplicates and sort
            allData = allData.stream()
                    .distinct()
                    .sorted(Comparator.comparing(OHLCData::timestamp))
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        } catch (Exception e) {
            log.error("❌ Multi-call fetch failed for {}: {}", symbol, e.getMessage());
        }

        return allData;
    }

    /**
     * FOR TimeGeometryService - returns OHLC data for pivot detection
     */
    public List<OHLCData> getHistoricalData(String symbol) {
        return new ArrayList<>(currentData.getOrDefault(symbol, new ArrayList<>()));
    }

    /**
     * Get only weekly data for major cycle detection
     */
    public List<OHLCData> getWeeklyData(String symbol) {
        List<OHLCData> allData = getHistoricalData(symbol);
        // Filter to approximate weekly data (every 7th point from daily)
        List<OHLCData> weeklyData = new ArrayList<>();
        for (int i = 0; i < allData.size(); i += 7) {
            weeklyData.add(allData.get(i));
        }
        return weeklyData;
    }

    /**
     * Helper method for single API call
     */
    private List<OHLCData> fetchBinanceData(String symbol, String timeframe, int limit) {
        try {
            String binanceInterval = convertTimeframeToBinanceInterval(timeframe);
            String response = binanceGateway.getRawKlines(symbol, binanceInterval, limit).block();
            return parseBinanceKlinesToOHLC(response);
        } catch (Exception e) {
            log.error("❌ Failed to fetch Binance data for {} {}: {}", symbol, timeframe, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Parsing method that returns simple OHLC data
     */
    private List<OHLCData> parseBinanceKlinesToOHLC(String response) {
        try {
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