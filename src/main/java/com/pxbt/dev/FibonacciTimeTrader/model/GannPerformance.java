package com.pxbt.dev.FibonacciTimeTrader.model;

import lombok.Data;

import java.util.Map;

@Data
public class GannPerformance {
    private String symbol;
    private Map<Integer, Double> averageReturns; // Period -> avg % return
    private Map<Integer, Double> successRates;   // Period -> % success
    private Map<Integer, Integer> sampleSizes;   // Period -> number of samples
}