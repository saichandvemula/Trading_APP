package com.example.demo.dto;

import java.time.Instant;
import java.util.List;

public record OptionChainResponse(
        String stockName,
        Instant generatedAt,
        List<OptionSnapshotDto> options
) {
}
