package com.pxbt.dev.FibonacciTimeTrader.model;

import lombok.Data;

import java.time.LocalDate;

@Data
public class FibonacciTimeProjection {
    private LocalDate date;
    private int fibonacciNumber;
    private PricePivot sourcePivot;
    private double intensity;
    private String type; // "SUPPORT", "RESISTANCE", "REVERSAL"
    private String description;
}