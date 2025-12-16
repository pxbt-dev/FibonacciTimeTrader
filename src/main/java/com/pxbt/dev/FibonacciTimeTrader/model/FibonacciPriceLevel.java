package com.pxbt.dev.FibonacciTimeTrader.model;

import lombok.Data;

@Data
public class FibonacciPriceLevel {
    private double price;
    private double ratio;
    private String label;
    private String type; // "SUPPORT" or "RESISTANCE"
    private String distanceFromHigh; // e.g., "23.6% retracement"

    public FibonacciPriceLevel(double price, double ratio, String label, String type, String distanceFromHigh) {
        this.price = price;
        this.ratio = ratio;
        this.label = label;
        this.type = type;
        this.distanceFromHigh = distanceFromHigh;
    }
}