package com.pxbt.dev.FibonacciTimeTrader.controller;

import com.pxbt.dev.FibonacciTimeTrader.model.VortexAnalysis;
import com.pxbt.dev.FibonacciTimeTrader.model.VortexWindow;
import com.pxbt.dev.FibonacciTimeTrader.service.TimeGeometryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/time-geometry")
public class TimeGeometryController {

    @Autowired
    private TimeGeometryService timeGeometryService;

    @GetMapping("/analysis/{symbol}")
    public ResponseEntity<VortexAnalysis> getAnalysis(@PathVariable String symbol) {
        try {
            VortexAnalysis analysis = timeGeometryService.analyzeSymbol(symbol);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/upcoming-windows/{symbol}")
    public ResponseEntity<List<VortexWindow>> getUpcomingWindows(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "30") int daysAhead) {

        VortexAnalysis analysis = timeGeometryService.analyzeSymbol(symbol);
        LocalDate cutoff = LocalDate.now().plusDays(daysAhead);

        List<VortexWindow> upcoming = analysis.getVortexWindows().stream()
                .filter(window -> !window.getDate().isBefore(LocalDate.now()))
                .filter(window -> !window.getDate().isAfter(cutoff))
                .collect(Collectors.toList());

        return ResponseEntity.ok(upcoming);
    }
}