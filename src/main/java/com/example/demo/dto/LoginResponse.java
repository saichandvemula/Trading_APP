package com.example.demo.dto;

import java.time.Instant;

public record LoginResponse(
        boolean authenticated,
        String clientCode,
        Instant createdAt,
        Instant expiresAt
) {
}
