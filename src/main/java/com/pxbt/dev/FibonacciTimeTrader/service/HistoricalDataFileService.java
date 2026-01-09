package com.pxbt.dev.FibonacciTimeTrader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pxbt.dev.FibonacciTimeTrader.service.BinanceHistoricalService.OHLCData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Slf4j
@Service
public class HistoricalDataFileService {
    private static final String DATA_DIR = "historical_data/";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HistoricalDataFileService() {
        // Create data directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(DATA_DIR));
            log.info("📁 Created/verified data directory: {}", DATA_DIR);
        } catch (IOException e) {
            log.error("❌ Failed to create data directory: {}", e.getMessage());
        }
    }

    /**
     * Save historical data to file
     */
    public void saveHistoricalData(String symbol, String interval, List<OHLCData> data) {
        if (data == null || data.isEmpty()) {
            log.warn("⚠️ No data to save for {} {}", symbol, interval);
            return;
        }

        String filename = getFilename(symbol, interval);

        try {
            // Write to temp file first (atomic operation)
            Path tempFile = Paths.get(filename + ".tmp");
            Path finalFile = Paths.get(filename);

            // Create parent directories if needed
            Files.createDirectories(tempFile.getParent());

            // Write data
            objectMapper.writeValue(tempFile.toFile(), data);

            // Atomically move temp file to final location
            Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING);

            log.info("💾 Saved {} candles for {} {} to {}",
                    data.size(), symbol, interval, filename);

        } catch (IOException e) {
            log.error("❌ Failed to save data for {} {}: {}", symbol, interval, e.getMessage());
        }
    }

    /**
     * Load historical data from file
     */
    public List<OHLCData> loadHistoricalData(String symbol, String interval) {
        String filename = getFilename(symbol, interval);

        try {
            File file = new File(filename);
            if (!file.exists()) {
                log.debug("📭 No data file for {} {}", symbol, interval);
                return new ArrayList<>();
            }

            if (file.length() == 0) {
                log.warn("⚠️ Empty file for {} {}", symbol, interval);
                return new ArrayList<>();
            }

            List<OHLCData> data = objectMapper.readValue(
                    file,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, OHLCData.class)
            );

            log.debug("📖 Loaded {} candles for {} {} from file",
                    data.size(), symbol, interval);

            return data;

        } catch (IOException e) {
            log.warn("⚠️ Failed to load data for {} {}: {}", symbol, interval, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Check if data needs updating
     */
    public boolean needsUpdate(String symbol, String interval, int maxAgeHours) {
        String filename = getFilename(symbol, interval);
        File file = new File(filename);

        // No file exists = needs update
        if (!file.exists()) {
            log.debug("📭 No file for {} {} - needs update", symbol, interval);
            return true;
        }

        // Check file age
        long lastModified = file.lastModified();
        long currentTime = System.currentTimeMillis();
        long ageInHours = (currentTime - lastModified) / (1000 * 60 * 60);

        boolean needsUpdate = ageInHours > maxAgeHours;

        if (needsUpdate) {
            log.debug("🕐 {} {} file is {} hours old (max: {}) - needs update",
                    symbol, interval, ageInHours, maxAgeHours);
        } else {
            log.debug("✅ {} {} file is {} hours old (max: {}) - still fresh",
                    symbol, interval, ageInHours, maxAgeHours);
        }

        return needsUpdate;
    }

    /**
     * Force refresh by deleting old file
     */
    public void forceRefresh(String symbol) {
        String[] intervals = {"1d", "1w", "1M"};

        for (String interval : intervals) {
            String filename = getFilename(symbol, interval);
            File file = new File(filename);

            if (file.exists() && file.delete()) {
                log.info("🗑️ Deleted old data file for {} {}", symbol, interval);
            }
        }

        // Also clear from any caches if needed
    }

    /**
     * Get all available symbols from files
     */
    public List<String> getAvailableSymbols() {
        File dir = new File(DATA_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            return new ArrayList<>();
        }

        Set<String> symbols = new HashSet<>();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));

        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                // Extract symbol from filename like "BTC_1d.json"
                if (name.contains("_")) {
                    String symbol = name.substring(0, name.indexOf('_'));
                    symbols.add(symbol);
                }
            }
        }

        log.debug("📁 Found {} symbols in data directory", symbols.size());
        return new ArrayList<>(symbols);
    }

    /**
     * Get file statistics
     */
    public Map<String, Object> getFileStats() {
        Map<String, Object> stats = new HashMap<>();
        File dir = new File(DATA_DIR);

        if (!dir.exists()) {
            stats.put("directoryExists", false);
            return stats;
        }

        stats.put("directoryExists", true);
        stats.put("directoryPath", dir.getAbsolutePath());

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files != null) {
            stats.put("fileCount", files.length);

            long totalSize = 0;
            for (File file : files) {
                totalSize += file.length();
            }
            stats.put("totalSizeMB", totalSize / (1024 * 1024));

            // List files
            List<String> fileList = new ArrayList<>();
            for (File file : files) {
                fileList.add(file.getName() + " (" + (file.length() / 1024) + " KB)");
            }
            stats.put("files", fileList);
        }

        return stats;
    }

    private String getFilename(String symbol, String interval) {
        // Clean symbol and interval for filename safety
        String cleanSymbol = symbol.toUpperCase().replaceAll("[^A-Z0-9]", "");
        String cleanInterval = interval.replaceAll("[^a-zA-Z0-9]", "");

        return DATA_DIR + cleanSymbol + "_" + cleanInterval + ".json";
    }
}