package com.example.demo.dto;

import com.example.demo.domain.SignalDirection;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record WeeklyPredictionResponse(
        String stockName,
        String exchange,
        String symbolToken,
        LocalDate fromDate,
        LocalDate toDate,
        SignalDirection direction,
        int confidence,
        BigDecimal weeklyChangePercent,
        BigDecimal lastClose,
        AdvancedTechnicalSummaryDto advancedTechnical,
        WeeklyTradePlanDto tradePlan,
        String reason,
        Instant generatedAt,
        List<HistoricalCandleDto> candles
) {
}
