package com.pxbt.dev.FibonacciTimeTrader.controller;

import com.pxbt.dev.FibonacciTimeTrader.model.LunarEvent;
import com.pxbt.dev.FibonacciTimeTrader.service.LunarDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/lunar")
public class LunarController {

    @Autowired
    private LunarDataService lunarDataService;

    @GetMapping("/events")
    public List<LunarEvent> getLunarEvents() {
        return lunarDataService.getLunarEvents();
    }

    @GetMapping("/events/upcoming")
    public List<LunarEvent> getUpcomingEvents(@RequestParam(defaultValue = "10") int limit) {
        return lunarDataService.getUpcomingEvents(limit);
    }

    @GetMapping("/events/type/{type}")
    public List<LunarEvent> getEventsByType(@PathVariable String type) {
        return lunarDataService.getEventsByType(type);
    }

    @GetMapping("/events/range")
    public List<LunarEvent> getEventsInRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date end) {
        return lunarDataService.getEventsBetween(start, end);
    }
}