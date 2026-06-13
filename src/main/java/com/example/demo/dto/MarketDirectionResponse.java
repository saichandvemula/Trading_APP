package com.example.demo.dto;

import com.example.demo.domain.SignalDirection;
import java.time.Instant;

public record MarketDirectionResponse(
        String stockName,
        SignalDirection direction,
        int confidence,
        String reason,
        Instant generatedAt
) {
}
