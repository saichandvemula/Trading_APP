package com.example.demo.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdvancedTechnicalSummaryDto(
        BigDecimal ema5,
        BigDecimal ema20,
        BigDecimal rsi14,
        BigDecimal macd,
        BigDecimal macdSignal,
        BigDecimal atr14,
        BigDecimal atrPercent,
        BigDecimal support,
        BigDecimal resistance,
        BigDecimal closePositionPercent,
        BigDecimal averageVolume,
        BigDecimal latestVolumeRatio,
        List<String> candlestickPatterns,
        String trend,
        String momentum,
        String volatility,
        String volumeSignal
) {
}
