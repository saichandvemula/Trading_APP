package com.example.demo.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading.universe")
public record Nifty50Properties(
        List<Stock> nifty50
) {
    public record Stock(
            String symbol,
            String exchange,
            String token
    ) {
    }
}
