package com.example.demo.dto;

import com.example.demo.domain.InstrumentType;
import java.time.Instant;
import java.util.List;

public record OptionChainResponse(
        InstrumentType instrument,
        Instant generatedAt,
        List<OptionSnapshotDto> options
) {
}
