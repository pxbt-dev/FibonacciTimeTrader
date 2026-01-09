package com.pxbt.dev.FibonacciTimeTrader.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Component
public class DataUpdateScheduler {

    @Autowired
    private BinanceHistoricalService binanceService;

    // Daily update at 2 AM
    @Scheduled(cron = "0 0 2 * * *")
    public void dailyDataUpdate() {
        log.info("🔄 Starting daily data update");

        for (String symbol : Arrays.asList("BTC", "SOL", "TAO", "WIF")) {
            try {
                binanceService.backgroundUpdate(symbol);
                Thread.sleep(2000); // 2 seconds between symbols
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Failed to update {}", symbol, e);
            }
        }

        log.info("✅ Daily data update complete");
    }

    // Weekend full refresh
    @Scheduled(cron = "0 0 4 * * SAT")
    public void weekendFullUpdate() {
        log.info("🎯 Starting weekend full data refresh");

        // Force fresh fetch for all symbols
        for (String symbol : Arrays.asList("BTC", "SOL", "TAO", "WIF")) {
            try {
                // This would fetch full datasets again
                binanceService.getHistoricalData(symbol);
                Thread.sleep(5000); // 5 seconds between full fetches
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}