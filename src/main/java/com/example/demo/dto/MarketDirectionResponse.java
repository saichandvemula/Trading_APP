package com.example.demo.dto;

import com.example.demo.domain.InstrumentType;
import com.example.demo.domain.SignalDirection;
import java.time.Instant;

public record MarketDirectionResponse(
        InstrumentType instrument,
        SignalDirection direction,
        int confidence,
        String reason,
        Instant generatedAt
) {
}
