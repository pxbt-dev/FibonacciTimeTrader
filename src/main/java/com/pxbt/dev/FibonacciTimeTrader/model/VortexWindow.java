package com.pxbt.dev.FibonacciTimeTrader.model;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class VortexWindow {
    private LocalDate date;
    private String type; // "IMMINENT", "MAJOR_RESONANCE", "CYCLE_CONVERGENCE", "STANDARD_VORTEX"
    private double intensity;
    private List<String> contributingFactors;
    private String description;
}