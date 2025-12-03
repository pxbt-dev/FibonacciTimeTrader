package com.pxbt.dev.FibonacciTimeTrader.model;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PricePivot {
    private LocalDate date;
    private double price;
    private String type; // "HIGH" or "LOW"
    private double strength; // 0-1 scale

    public PricePivot(LocalDate date, double price, String type, double strength) {
        this.date = date;
        this.price = price;
        this.type = type;
        this.strength = strength;
    }
}
