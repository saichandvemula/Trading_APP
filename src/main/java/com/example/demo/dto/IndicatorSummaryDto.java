package com.example.demo.dto;

import java.math.BigDecimal;

public record IndicatorSummaryDto(
        BigDecimal pcr,
        long totalCallOi,
        long totalPutOi,
        long totalCallOiChange,
        long totalPutOiChange,
        BigDecimal averageVwap,
        BigDecimal callPriceMovement,
        BigDecimal putPriceMovement,
        boolean volumeSpike
) {
}
