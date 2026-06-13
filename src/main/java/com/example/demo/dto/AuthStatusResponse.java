package com.example.demo.dto;

import java.time.Instant;

public record AuthStatusResponse(
        boolean authenticated,
        String clientCode,
        Instant createdAt,
        Instant expiresAt,
        boolean feedTokenAvailable
) {
}
