package com.example.demo.dto;

import java.time.Instant;
import java.util.List;

public record Nifty50WeeklyPredictionResponse(
        Instant generatedAt,
        int total,
        int success,
        int failed,
        List<WeeklyPredictionSummaryDto> results
) {
}
