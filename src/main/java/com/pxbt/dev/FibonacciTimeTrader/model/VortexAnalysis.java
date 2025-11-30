package com.pxbt.dev.FibonacciTimeTrader.model;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class VortexAnalysis {
    private String symbol;
    private LocalDate analysisTime;
    private List<FibonacciTimeProjection> fibonacciTimeProjections;
    private List<GannDate> gannDates;
    private List<CycleConvergence> cycleConvergences;
    private List<VortexWindow> vortexWindows;
    private double compressionScore;
    private double confidenceScore;

    // Add constructor for convenience
    public VortexAnalysis() {
        this.analysisTime = LocalDate.now();
    }

    // Manual setters if Lombok isn't working (temporary fix)
    public void setFibonacciTimeProjections(List<FibonacciTimeProjection> fibonacciTimeProjections) {
        this.fibonacciTimeProjections = fibonacciTimeProjections;
    }

    public void setGannDates(List<GannDate> gannDates) {
        this.gannDates = gannDates;
    }

    public void setCycleConvergences(List<CycleConvergence> cycleConvergences) {
        this.cycleConvergences = cycleConvergences;
    }

    public void setVortexWindows(List<VortexWindow> vortexWindows) {
        this.vortexWindows = vortexWindows;
    }

    public void setCompressionScore(double compressionScore) {
        this.compressionScore = compressionScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setAnalysisTime(LocalDate analysisTime) {
        this.analysisTime = analysisTime;
    }
}
