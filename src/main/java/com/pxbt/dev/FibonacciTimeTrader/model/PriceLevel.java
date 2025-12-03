package com.pxbt.dev.FibonacciTimeTrader.model;

import lombok.Data;

@Data
public class PriceLevel {
    private double price;
    private String type; // "SUPPORT", "RESISTANCE"
    private double strength;
}
