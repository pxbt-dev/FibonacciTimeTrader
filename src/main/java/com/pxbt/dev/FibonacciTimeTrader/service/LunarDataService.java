package com.pxbt.dev.FibonacciTimeTrader.service;

import com.pxbt.dev.FibonacciTimeTrader.model.LunarEvent;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;

@Service
public class LunarDataService {
    private static final Logger log = LoggerFactory.getLogger(LunarDataService.class);

    private final List<LunarEvent> lunarEvents = new ArrayList<>();
    private boolean dataLoaded = false;

    // Formatter for dates like "29 Jan" with year context
    @Getter
    @Setter
    private DateTimeFormatter dateFormatter = new DateTimeFormatterBuilder()
            .appendPattern("d MMM")
            .parseDefaulting(ChronoField.YEAR, 2025) // Default year, will adjust
            .toFormatter(Locale.ENGLISH);

    // Formatter for time like "12:35"
    @Getter
    @Setter
    private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    public LunarDataService() {
        log.info("Initializing LunarDataService...");
        loadLunarData();
    }

    public synchronized void loadLunarData() {
        if (dataLoaded) return;

        lunarEvents.clear();
        log.info("Loading lunar cycle data from CSV...");

        try {
            ClassPathResource resource = new ClassPathResource("lunar_events.csv");
            if (!resource.exists()) {
                log.warn("Lunar data file not found at: data/lunar_events.csv");
                return;
            }

            try (InputStream inputStream = resource.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

                String line;
                int lineNumber = 0;
                int newMoonCount = 0;
                int fullMoonCount = 0;

                // Read header
                String header = reader.readLine();
                lineNumber++;
                log.debug("CSV Header: {}", header);

                // Parse each line
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    line = line.trim();

                    if (line.isEmpty()) {
                        continue;
                    }

                    try {
                        parseLunarCycle(line, lineNumber);
                    } catch (Exception e) {
                        log.warn("Failed to parse line {}: {}. Line: {}", lineNumber, e.getMessage(), line);
                    }
                }

                log.info("Loaded {} lunar events ({} New Moons, {} Full Moons)",
                        lunarEvents.size(), newMoonCount, fullMoonCount);
                dataLoaded = true;

            } catch (Exception e) {
                log.error("Error reading lunar CSV file: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("Failed to load lunar data: {}", e.getMessage());
        }
    }

    private void parseLunarCycle(String line, int lineNumber) {
        try {
            // Split by comma
            String[] parts = line.split(",");

            if (parts.length < 5) {
                log.debug("Line {} has insufficient columns: {}", lineNumber, parts.length);
                return;
            }

            // Clean parts
            for (int i = 0; i < parts.length; i++) {
                parts[i] = parts[i] != null ? parts[i].trim() : "";
            }

            // Lunation number (like "1263")
            String lunation = parts[0];

            // Parse New Moon data
            if (parts[1] != null && !parts[1].isEmpty() &&
                    parts[2] != null && !parts[2].isEmpty()) {
                LunarEvent newMoonEvent = createLunarEvent(
                        "NEW_MOON",
                        parts[1],  // Date like "29 Jan"
                        parts[2],  // Time like "12:35"
                        lunation,
                        "Lunation " + lunation + " - New Moon",
                        lineNumber * 2 - 1  // Odd ID for New Moon
                );
                if (newMoonEvent != null) {
                    lunarEvents.add(newMoonEvent);
                }
            }

            // Parse Full Moon data
            if (parts[3] != null && !parts[3].isEmpty() &&
                    parts[4] != null && !parts[4].isEmpty()) {
                LunarEvent fullMoonEvent = createLunarEvent(
                        "FULL_MOON",
                        parts[3],  // Date like "12 Feb"
                        parts[4],  // Time like "13:53"
                        lunation,
                        "Lunation " + lunation + " - Full Moon",
                        lineNumber * 2  // Even ID for Full Moon
                );
                if (fullMoonEvent != null) {
                    lunarEvents.add(fullMoonEvent);
                }
            }

        } catch (Exception e) {
            log.warn("Error parsing lunar cycle on line {}: {}", lineNumber, e.getMessage());
        }
    }

    private LunarEvent createLunarEvent(String eventType, String dateStr, String timeStr,
                                        String lunation, String description, int id) {
        try {
            // Parse date and time
            LocalDateTime dateTime = parseDateTime(dateStr, timeStr);
            if (dateTime == null) {
                log.debug("Could not parse date/time: {} {}", dateStr, timeStr);
                return null;
            }

            // Create event
            LunarEvent event = new LunarEvent();
            event.setId(id);
            event.setDate(Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant()));
            event.setEventType(eventType);
            event.setEventName(eventType.equals("NEW_MOON") ? "New Moon" : "Full Moon");
            event.setDetails(description);
            event.setLunation(lunation);

            return event;

        } catch (Exception e) {
            log.warn("Error creating lunar event: {} {} - {}", dateStr, timeStr, e.getMessage());
            return null;
        }
    }

    private LocalDateTime parseDateTime(String dateStr, String timeStr) {
        try {
            // Now with years included in dateStr (e.g., "29 Jan 2025")
            // Combine date and time: "29 Jan 2025 12:35"
            String fullDateTime = dateStr + " " + timeStr;

            // Parse directly to LocalDateTime
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ENGLISH);
            return LocalDateTime.parse(fullDateTime, formatter);

        } catch (Exception e) {
            log.warn("Failed to parse date/time: {} {} - {}", dateStr, timeStr, e.getMessage());
            return null;
        }
    }

    public List<LunarEvent> getLunarEvents() {
        if (!dataLoaded) {
            loadLunarData();
        }
        return new ArrayList<>(lunarEvents);
    }

    public List<LunarEvent> getUpcomingEvents(int limit) {
        List<LunarEvent> allEvents = getLunarEvents();
        Date now = new Date();

        return allEvents.stream()
                .filter(event -> event.getDate() != null && event.getDate().after(now))
                .sorted(Comparator.comparing(LunarEvent::getDate))
                .limit(limit)
                .toList();
    }

    public List<LunarEvent> getEventsByType(String eventType) {
        return getLunarEvents().stream()
                .filter(event -> event.getEventType().equalsIgnoreCase(eventType))
                .sorted(Comparator.comparing(LunarEvent::getDate))
                .toList();
    }

    public List<LunarEvent> getEventsBetween(Date startDate, Date endDate) {
        return getLunarEvents().stream()
                .filter(event -> event.getDate() != null)
                .filter(event -> !event.getDate().before(startDate) && !event.getDate().after(endDate))
                .sorted(Comparator.comparing(LunarEvent::getDate))
                .toList();
    }

}