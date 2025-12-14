package com.pxbt.dev.FibonacciTimeTrader.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HighRiskDate {
    private LocalDateTime date;
    private Double kp;
    private Integer flux;
    private String stormLevel;
    private Integer riskScore;
}
