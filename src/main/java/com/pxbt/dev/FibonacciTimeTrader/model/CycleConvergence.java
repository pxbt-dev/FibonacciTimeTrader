package com.pxbt.dev.FibonacciTimeTrader.model;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CycleConvergence {
    private LocalDate date;
    private List<String> convergingSignals;
    private double strength;
    private String expectedImpact;
}