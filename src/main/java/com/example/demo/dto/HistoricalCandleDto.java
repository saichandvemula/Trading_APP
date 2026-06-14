package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record HistoricalCandleDto(
        OffsetDateTime timestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {
}
