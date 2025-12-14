package com.pxbt.dev.FibonacciTimeTrader.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FluxForecastDay {
    private LocalDateTime date;
    private Integer flux;
    private String solarActivity;
    private String rawDate;
}