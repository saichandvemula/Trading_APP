package com.example.demo.dto;

import com.example.demo.domain.InstrumentType;
import java.math.BigDecimal;
import java.time.Instant;

public record MarketTickDto(
        InstrumentType instrument,
        String symbol,
        String token,
        BigDecimal lastTradedPrice,
        Long volume,
        Long openInterest,
        Instant tickTime
) {
}
