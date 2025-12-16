package com.pxbt.dev.FibonacciTimeTrader.controller;

import com.pxbt.dev.FibonacciTimeTrader.model.*;
import com.pxbt.dev.FibonacciTimeTrader.service.TimeGeometryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/timeGeometry")
public class TimeGeometryController {

    @Autowired
    private TimeGeometryService timeGeometryService;


    @GetMapping("/analysis/{symbol}")
    public ResponseEntity<?> getAnalysis(@PathVariable String symbol) {
        try {
            VortexAnalysis analysis = timeGeometryService.analyzeSymbol(symbol);

            Map<String, Object> response = new HashMap<>();
            response.put("vortexWindows", analysis.getVortexWindows());
            response.put("fibonacciTimeProjections", analysis.getFibonacciTimeProjections());
            response.put("fibonacciPriceLevels", analysis.getFibonacciPriceLevels());
            response.put("confidenceScore", analysis.getConfidenceScore());
            response.put("compressionScore", analysis.getCompressionScore());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to analyze symbol", "message", e.getMessage()));
        }
    }
}