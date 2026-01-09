package com.pxbt.dev.FibonacciTimeTrader.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    @GetMapping("/stats")
    public String getMemoryStats() {
        Runtime rt = Runtime.getRuntime();
        long total = rt.totalMemory();
        long free = rt.freeMemory();
        long used = total - free;
        long max = rt.maxMemory();

        return String.format(
                "MEMORY: Used=%,d MB, Free=%,d MB, Total=%,d MB, Max=%,d MB",
                used / (1024 * 1024),
                free / (1024 * 1024),
                total / (1024 * 1024),
                max / (1024 * 1024)
        );
    }
}