package com.example.demo.dto;

import com.example.demo.domain.InstrumentType;
import com.example.demo.domain.SignalDirection;
import java.math.BigDecimal;
import java.time.Instant;

public record TradingSignalDto(
        InstrumentType instrument,
        SignalDirection direction,
        int confidence,
        String reason,
        BigDecimal pcr,
        BigDecimal cePriceMovement,
        BigDecimal pePriceMovement,
        boolean volumeSpike,
        Instant generatedAt
) {
}
