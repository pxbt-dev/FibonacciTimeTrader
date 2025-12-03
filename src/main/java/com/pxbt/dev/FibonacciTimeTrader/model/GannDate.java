package com.pxbt.dev.FibonacciTimeTrader.model;

import lombok.Data;

import java.time.LocalDate;

@Data
public class GannDate {
    private LocalDate date;
    private String type;
    private PricePivot sourcePivot;

    public GannDate(LocalDate date, String type, PricePivot sourcePivot) {
        this.date = date;
        this.type = type;
        this.sourcePivot = sourcePivot;
    }
}
