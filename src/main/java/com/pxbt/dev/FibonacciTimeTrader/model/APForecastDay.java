package com.pxbt.dev.FibonacciTimeTrader.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class APForecastDay {
    private LocalDateTime date;
    private Integer ap;
    private Double kp;
    private String stormLevel;
    private String rawDate;
}