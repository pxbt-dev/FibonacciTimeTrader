package com.pxbt.dev.FibonacciTimeTrader.service;

import com.pxbt.dev.FibonacciTimeTrader.model.ForecastDay;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class NoaaParserService {

    private final DateTimeFormatter dateFormatter =
            DateTimeFormatter.ofPattern("ddMMMyy", Locale.US);

    /**
     * Parse 45-day AP forecast - stops at Flux section
     */
    public List<ForecastDay> parseSolarForecast(String forecastText) {
        List<ForecastDay> highApDays = new ArrayList<>();
        if (forecastText == null) return highApDays;

        String[] lines = forecastText.split("\n");
        boolean inApSection = false;

        for (String line : lines) {
            line = line.trim();

            if (line.startsWith("45-DAY AP FORECAST")) {
                inApSection = true;
                continue;
            }

            if (line.startsWith("45-DAY F10.7 CM FLUX FORECAST")) {
                break;
            }

            if (inApSection && !line.isEmpty()) {
                String[] tokens = line.split("\\s+");

                for (int i = 0; i < tokens.length - 1; i += 2) {
                    try {
                        LocalDate date = LocalDate.parse(tokens[i], dateFormatter);
                        int ap = Integer.parseInt(tokens[i + 1]);

                        if (ap >= 12) {
                            ForecastDay day = new ForecastDay();
                            day.setDate(date);
                            day.setAp(ap);
                            highApDays.add(day);
                        }
                    } catch (Exception e) {
                        // Skip parse errors
                    }
                }
            }
        }

        log.info("Parsed {} AP days (AP ≥ 12)", highApDays.size());
        return highApDays;
    }

    /**
     * Parse issue date from header
     * Format: ":Issued: 2024 Jan 15 2202 UTC"
     */
    public String parseIssueDate(String forecastText) {
        if (forecastText == null) return null;

        for (String line : forecastText.split("\n")) {
            if (line.startsWith(":Issued:")) {
                return line.substring(":Issued:".length()).trim();
            }
        }
        return null;
    }
}