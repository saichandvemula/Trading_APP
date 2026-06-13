package com.example.demo.service;

import com.example.demo.config.AngelOneProperties;
import com.example.demo.domain.AuthSessionEntity;
import com.example.demo.dto.AuthStatusResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.exception.AuthenticationRequiredException;
import com.example.demo.exception.SmartApiException;
import com.example.demo.repository.AuthSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AngelOneProperties properties;
    private final SmartApiClient smartApiClient;
    private final AuthSessionRepository authSessionRepository;
    private final TotpService totpService;

    public AuthService(
            AngelOneProperties properties,
            SmartApiClient smartApiClient,
            AuthSessionRepository authSessionRepository,
            TotpService totpService
    ) {
        this.properties = properties;
        this.smartApiClient = smartApiClient;
        this.authSessionRepository = authSessionRepository;
        this.totpService = totpService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String clientCode = firstNonBlank(request.clientCode(), properties.clientCode());
        String password = firstNonBlank(request.password(), properties.password());
        String totp = firstNonBlank(request.totp(), null);
        if (totp == null) {
            totp = totpService.generate(properties.totpSecret());
        }
        if (isBlank(clientCode) || isBlank(password)) {
            throw new SmartApiException("Angel One client code and password must be configured or provided");
        }

        JsonNode response = smartApiClient.login(clientCode, password, totp);
        JsonNode data = response.path("data");
        AuthSessionEntity session = new AuthSessionEntity();
        session.setClientCode(clientCode);
        session.setJwtToken(requiredText(data, "jwtToken"));
        session.setRefreshToken(data.path("refreshToken").asText(null));
        session.setFeedToken(requiredText(data, "feedToken"));
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(12, ChronoUnit.HOURS));
        authSessionRepository.save(session);
        log.info("Angel One login succeeded for client {}", clientCode);
        return new LoginResponse(true, clientCode, session.getCreatedAt(), session.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public AuthStatusResponse status() {
        return authSessionRepository.findTopByClientCodeOrderByCreatedAtDesc(properties.clientCode())
                .map(session -> new AuthStatusResponse(
                        !isExpired(session),
                        session.getClientCode(),
                        session.getCreatedAt(),
                        session.getExpiresAt(),
                        !isBlank(session.getFeedToken())
                ))
                .orElse(new AuthStatusResponse(false, properties.clientCode(), null, null, false));
    }

    @Transactional(readOnly = true)
    public AuthSessionEntity requireSession() {
        AuthSessionEntity session = authSessionRepository.findTopByClientCodeOrderByCreatedAtDesc(properties.clientCode())
                .orElseThrow(() -> new AuthenticationRequiredException("Login with Angel One before requesting market data"));
        if (isExpired(session)) {
            throw new AuthenticationRequiredException("Angel One session is expired. Login again.");
        }
        return session;
    }

    private boolean isExpired(AuthSessionEntity session) {
        return session.getExpiresAt() != null && session.getExpiresAt().isBefore(Instant.now());
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (isBlank(value)) {
            throw new SmartApiException("SmartAPI login response did not include " + field);
        }
        return value;
    }

    private String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : (!isBlank(second) ? second : null);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
