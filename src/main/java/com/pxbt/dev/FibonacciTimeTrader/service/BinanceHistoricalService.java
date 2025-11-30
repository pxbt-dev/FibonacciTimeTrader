package com.pxbt.dev.FibonacciTimeTrader.service;

import com.pxbt.dev.FibonacciTimeTrader.Gateway.BinanceGateway;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class BinanceHistoricalService {

    private final BinanceGateway binanceGateway;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Simple record for OHLC data (no Lombok needed)
    record OHLCData(long timestamp, double open, double high, double low, double close, double volume) {}

    private final Map<String, List<OHLCData>> currentData = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("📚 BinanceHistoricalService initializing for Time Geometry...");
        loadAllDataInOneCall();
    }

    private void loadAllDataInOneCall() {
        try {
            Map<String, List<OHLCData>> allData = new HashMap<>();
            String[] symbols = {"BTC", "SOL", "TAO", "WIF"};

            for (String symbol : symbols) {
                List<OHLCData> symbolData = fetchBinanceData(symbol, "1d", 365);
                allData.put(symbol, symbolData);
                log.info("📊 {}: Loaded {} OHLC data points", symbol, symbolData.size());
            }

            currentData.putAll(allData);
            log.info("✅ Historical OHLC data loaded for Time Geometry");

        } catch (Exception e) {
            log.error("❌ Failed to load historical data: {}", e.getMessage());
            // Initialize empty data
            for (String symbol : new String[]{"BTC", "SOL", "TAO", "WIF"}) {
                currentData.put(symbol, new ArrayList<>());
            }
        }
    }

    /**
     * FOR TimeGeometryService - returns OHLC data for pivot detection
     */
    public List<OHLCData> getHistoricalData(String symbol) {
        return new ArrayList<>(currentData.getOrDefault(symbol, new ArrayList<>()));
    }

    /**
     * Helper method for batch fetching
     */
    private List<OHLCData> fetchBinanceData(String symbol, String timeframe, int limit) {
        try {
            String binanceInterval = convertTimeframeToBinanceInterval(timeframe);
            String response = binanceGateway.getRawKlines(symbol, binanceInterval, limit).block();
            return parseBinanceKlinesToOHLC(response);
        } catch (Exception e) {
            log.error("❌ Failed to fetch Binance data for {}: {}", symbol, e.getMessage());
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
            default -> "1d"; // Default to daily for time geometry
        };
    }
}