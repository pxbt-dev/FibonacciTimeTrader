package com.pxbt.dev.FibonacciTimeTrader.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CurrentSolarData {
    private Double currentKp;
    private Integer currentFlux;
    private LocalDateTime updatedAt;
}
