package com.example.demo.service;

import com.example.demo.config.AngelOneProperties;
import com.example.demo.domain.AuthSessionEntity;
import com.example.demo.exception.AuthenticationRequiredException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@Service
public class AngelOneWebSocketService {

    private static final Logger log = LoggerFactory.getLogger(AngelOneWebSocketService.class);

    private final AngelOneProperties properties;
    private final AuthService authService;
    private final MarketDataService marketDataService;
    private final ObjectMapper objectMapper;
    private volatile WebSocketSession session;

    public AngelOneWebSocketService(
            AngelOneProperties properties,
            AuthService authService,
            MarketDataService marketDataService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.authService = authService;
        this.marketDataService = marketDataService;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connectOnStartup() {
        if (!properties.websocket().enabled()) {
            log.info("Angel One WebSocket is disabled");
            return;
        }
        try {
            connect();
        } catch (AuthenticationRequiredException ex) {
            log.warn("Angel One WebSocket not connected: {}", ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Angel One WebSocket connection failed: {}", ex.getMessage());
        }
    }

    public synchronized boolean connect() {
        AuthSessionEntity auth = authService.requireSession();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.set("Authorization", auth.getJwtToken());
        headers.set("x-api-key", properties.apiKey());
        headers.set("x-client-code", auth.getClientCode());
        headers.set("x-feed-token", auth.getFeedToken());

        StandardWebSocketClient client = new StandardWebSocketClient();
        client.execute(new Handler(), headers, URI.create(properties.websocketUrl()));
        return true;
    }

    public boolean isConnected() {
        return session != null && session.isOpen();
    }

    private void subscribe(WebSocketSession webSocketSession) {
        List<String> tokens = properties.websocket().tokens();
        if (tokens == null || tokens.isEmpty()) {
            log.warn("No Angel One WebSocket tokens configured");
            return;
        }
        try {
            Map<String, Object> request = Map.of(
                    "correlationID", properties.websocket().correlationId(),
                    "action", 1,
                    "params", Map.of(
                            "mode", 3,
                            "tokenList", List.of(Map.of(
                                    "exchangeType", properties.websocket().exchangeType(),
                                    "tokens", tokens
                            ))
                    )
            );
            webSocketSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(request)));
            log.info("Subscribed Angel One WebSocket to {} tokens", tokens.size());
        } catch (Exception ex) {
            log.warn("Angel One WebSocket subscription failed: {}", ex.getMessage());
        }
    }

    private final class Handler extends AbstractWebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession webSocketSession) {
            session = webSocketSession;
            log.info("Angel One WebSocket connected");
            subscribe(webSocketSession);
        }

        @Override
        protected void handleTextMessage(WebSocketSession webSocketSession, TextMessage message) throws Exception {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String token = text(node, "token", "symbolToken");
            BigDecimal ltp = decimal(node, "lastTradedPrice", "ltp", "last_traded_price");
            if (token == null || ltp == null) {
                return;
            }
            String symbol = "99926009".equals(token) ? "BANKNIFTY" : "NIFTY";
            Long volume = longValue(node, "volume", "lastTradedQty");
            Long oi = longValue(node, "openInterest", "oi");
            marketDataService.saveTick(symbol, token, ltp, volume, oi, Instant.now());
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession webSocketSession, BinaryMessage message) {
            ByteBuffer buffer = message.getPayload().order(ByteOrder.LITTLE_ENDIAN);
            if (buffer.remaining() < 51) {
                log.debug("Ignoring short Angel One binary payload of {} bytes", message.getPayloadLength());
                return;
            }
            byte[] tokenBytes = new byte[25];
            buffer.position(2);
            buffer.get(tokenBytes);
            String token = new String(tokenBytes, StandardCharsets.UTF_8).replace("\u0000", "").trim();
            buffer.position(43);
            BigDecimal ltp = BigDecimal.valueOf(buffer.getLong()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            String symbol = "99926009".equals(token) ? "BANKNIFTY" : "NIFTY";
            marketDataService.saveTick(symbol, token, ltp, null, null, Instant.now());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus status) {
            session = null;
            log.warn("Angel One WebSocket closed: {}", status);
        }

        @Override
        public void handleTransportError(WebSocketSession webSocketSession, Throwable exception) {
            log.warn("Angel One WebSocket transport error: {}", exception.getMessage());
        }

        private String text(JsonNode node, String... fields) {
            for (String field : fields) {
                JsonNode value = node.path(field);
                if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) {
                    return value.asText();
                }
            }
            return null;
        }

        private BigDecimal decimal(JsonNode node, String... fields) {
            String value = text(node, fields);
            return value == null ? null : new BigDecimal(value.replace(",", ""));
        }

        private Long longValue(JsonNode node, String... fields) {
            String value = text(node, fields);
            return value == null ? null : new BigDecimal(value.replace(",", "")).longValue();
        }
    }
}
