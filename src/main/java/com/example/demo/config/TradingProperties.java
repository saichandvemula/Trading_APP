package com.example.demo.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "trading.signal")
public record TradingProperties(
        @Min(0) @Max(100) int minConfidence,
        @Positive double volumeSpikeMultiplier,
        @Min(0) @Max(100) int sidewaysScoreThreshold
) {
}
