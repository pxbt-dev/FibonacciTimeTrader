package com.pxbt.dev.FibonacciTimeTrader.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SolarForecast {

    //AP-related fields
    private Integer currentAp;
    private Long timestamp;
    private String message;
    private String source;
    private String issueDate;
    private List<ForecastDay> forecast;  // Contains AP values ≥ 12

    //Will keep other fields in case we use them later
    private String issued;
    private LocalDateTime fetchedAt;
    private String sourceUrl;
    private String forecaster;
}