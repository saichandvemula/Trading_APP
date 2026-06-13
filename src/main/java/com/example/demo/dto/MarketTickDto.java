package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketTickDto(
        String stockName,
        String symbol,
        String token,
        BigDecimal lastTradedPrice,
        Long volume,
        Long openInterest,
        Instant tickTime
) {
}
