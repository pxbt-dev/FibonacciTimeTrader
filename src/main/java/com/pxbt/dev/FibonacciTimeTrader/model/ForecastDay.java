package com.pxbt.dev.FibonacciTimeTrader.model;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ForecastDay {
    private LocalDate date;
    private Integer ap;
}