package com.pxbt.dev.FibonacciTimeTrader.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.Setter;

import java.util.Date;

@Setter
@Data
public class LunarEvent {
    // Getters and setters
    private Integer id;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private Date date;

    private String eventType; // NEW_MOON, FULL_MOON
    private String eventName;
    private String details;
    private String lunation; // Lunation number like "1263"

    @Override
    public String toString() {
        return "LunarEvent{" +
                "id=" + id +
                ", date=" + date +
                ", eventType='" + eventType + '\'' +
                ", eventName='" + eventName + '\'' +
                ", lunation='" + lunation + '\'' +
                '}';
    }
}