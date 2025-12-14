package com.pxbt.dev.FibonacciTimeTrader.model;

import lombok.Data;

import java.time.LocalDate;

@Data
public class FibonacciTimeProjection {
    private LocalDate date;
    private int fibonacciNumber; // Days (rounded for display)
    private double fibonacciRatio; // Store the actual ratio (0.382, 0.618, etc.)
    private PricePivot sourcePivot;
    private double intensity;
    private String type; // "SUPPORT", "RESISTANCE", "REVERSAL"
    private String description;

    // Optional: Add getter for label
    public String getFibLabel() {
        return String.format("Fib %.3f", fibonacciRatio);
    }

    // Helper to get days without rounding issues
    public double getExactDays() {
        return 100 * fibonacciRatio; // 0.786 → 78.6 days
    }
}