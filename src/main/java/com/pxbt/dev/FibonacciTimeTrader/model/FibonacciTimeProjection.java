package com.pxbt.dev.FibonacciTimeTrader.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Data
public class FibonacciTimeProjection {
    private LocalDate date;
    private int fibonacciNumber; // Days (rounded for display)
    private double fibonacciRatio; // Store the actual ratio (0.382, 0.618, etc.)
    @Setter
    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private PricePivot sourcePivot;
    private double intensity;
    private String type; // "SUPPORT", "RESISTANCE", "REVERSAL"
    private String description;

    // ✅ CRITICAL: Make sure this field exists
    private double priceTarget;

    // Optional: Add getter for label
    public String getFibLabel() {
        return String.format("Fib %.3f", fibonacciRatio);
    }

    // Helper to get days without rounding issues
    public double getExactDays() {
        return 100 * fibonacciRatio; // 0.786 → 78.6 days
    }

    // Helper to format price target
    public String getFormattedPriceTarget() {
        return priceTarget > 0 ? String.format("$%,.2f", priceTarget) : "N/A";
    }

}