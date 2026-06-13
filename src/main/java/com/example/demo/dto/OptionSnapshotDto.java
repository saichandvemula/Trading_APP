package com.example.demo.dto;

import com.example.demo.domain.InstrumentType;
import com.example.demo.domain.OptionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record OptionSnapshotDto(
        InstrumentType instrument,
        String symbol,
        String token,
        BigDecimal strike,
        OptionType optionType,
        LocalDate expiry,
        BigDecimal lastTradedPrice,
        Long volume,
        Long openInterest,
        Long oiChange,
        BigDecimal vwap,
        BigDecimal priceChange,
        Instant snapshotTime
) {
}
