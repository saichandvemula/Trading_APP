package com.example.demo.dto;

import com.example.demo.domain.SignalDirection;
import java.math.BigDecimal;

public record WeeklyPredictionSummaryDto(
        String stockName,
        String exchange,
        String symbolToken,
        SignalDirection direction,
        int confidence,
        BigDecimal weeklyChangePercent,
        BigDecimal lastClose,
        String trend,
        String momentum,
        String candlestickPatterns,
        String tradeAction,
        BigDecimal entryAbove,
        BigDecimal entryBelow,
        BigDecimal stopLoss,
        BigDecimal target1,
        BigDecimal target2,
        String status,
        String error
) {
    public static WeeklyPredictionSummaryDto success(WeeklyPredictionResponse response) {
        return new WeeklyPredictionSummaryDto(
                response.stockName(),
                response.exchange(),
                response.symbolToken(),
                response.direction(),
                response.confidence(),
                response.weeklyChangePercent(),
                response.lastClose(),
                response.advancedTechnical().trend(),
                response.advancedTechnical().momentum(),
                String.join(",", response.advancedTechnical().candlestickPatterns()),
                response.tradePlan().action(),
                response.tradePlan().entryAbove(),
                response.tradePlan().entryBelow(),
                response.tradePlan().stopLoss(),
                response.tradePlan().target1(),
                response.tradePlan().target2(),
                "SUCCESS",
                null
        );
    }

    public static WeeklyPredictionSummaryDto failed(String stockName, String exchange, String symbolToken, String error) {
        return new WeeklyPredictionSummaryDto(
                stockName,
                exchange,
                symbolToken,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "FAILED",
                error
        );
    }
}
