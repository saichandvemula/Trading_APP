package com.example.demo.service;

import com.example.demo.domain.OptionType;
import com.example.demo.exception.SmartApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class ScripMasterService {

    private static final String SCRIP_MASTER_URL = "https://margincalculator.angelbroking.com/OpenAPI_File/files/OpenAPIScripMaster.json";
    private static final DateTimeFormatter EXPIRY_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("ddMMMyyyy")
            .toFormatter(Locale.ENGLISH);
    private static final long CACHE_TTL_SECONDS = 24 * 60 * 60;

    private final RestTemplate restTemplate;
    private List<OptionContract> cachedContracts = List.of();
    private Instant cachedAt;

    public ScripMasterService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Optional<OptionContract> findOptionContract(
            String underlying,
            LocalDate expiry,
            BigDecimal strike,
            OptionType optionType
    ) {
        return optionContracts().stream()
                .filter(contract -> contract.matches(underlying, expiry, strike, optionType))
                .findFirst();
    }

    private synchronized List<OptionContract> optionContracts() {
        if (cachedAt != null && Instant.now().minusSeconds(CACHE_TTL_SECONDS).isBefore(cachedAt)) {
            return cachedContracts;
        }

        try {
            JsonNode response = restTemplate.getForObject(SCRIP_MASTER_URL, JsonNode.class);
            if (response == null || !response.isArray()) {
                throw new SmartApiException("Angel One scrip master response was empty or invalid");
            }

            List<OptionContract> contracts = new ArrayList<>();
            for (JsonNode item : response) {
                OptionContract contract = toOptionContract(item);
                if (contract != null) {
                    contracts.add(contract);
                }
            }
            cachedContracts = contracts;
            cachedAt = Instant.now();
            return cachedContracts;
        } catch (RestClientException ex) {
            throw new SmartApiException("Failed to download Angel One scrip master: " + ex.getMessage(), ex);
        }
    }

    private OptionContract toOptionContract(JsonNode item) {
        String exchange = text(item, "exch_seg", "exchange");
        String instrumentType = text(item, "instrumenttype", "instrumentType");
        if (!"NFO".equalsIgnoreCase(exchange) || instrumentType == null || !instrumentType.toUpperCase(Locale.ENGLISH).startsWith("OPT")) {
            return null;
        }

        String token = text(item, "token", "symbolToken", "symboltoken");
        String symbol = text(item, "symbol", "tradingSymbol", "tradingsymbol");
        String name = text(item, "name");
        String expiryText = text(item, "expiry");
        BigDecimal strike = normalizeStrike(decimal(item, "strike"));
        OptionType optionType = optionType(item, symbol);
        LocalDate expiry = parseExpiry(expiryText);
        if (token == null || symbol == null || name == null || expiry == null || strike == null || optionType == null) {
            return null;
        }

        return new OptionContract(
                name.trim().toUpperCase(Locale.ENGLISH),
                symbol,
                token,
                expiry,
                strike,
                optionType
        );
    }

    private BigDecimal normalizeStrike(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal normalized = value;
        if (value.compareTo(new BigDecimal("100000")) > 0) {
            normalized = value.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return normalized.setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate parseExpiry(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim().toUpperCase(Locale.ENGLISH), EXPIRY_FORMATTER);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(value.trim());
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }

    private OptionType optionType(JsonNode item, String symbol) {
        String value = text(item, "optionType", "option_type");
        if (value == null && symbol != null && symbol.length() >= 2) {
            value = symbol.substring(symbol.length() - 2);
        }
        if (value == null) {
            return null;
        }
        return value.toUpperCase(Locale.ENGLISH).contains("PE") ? OptionType.PE : OptionType.CE;
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

    public record OptionContract(
            String underlying,
            String symbol,
            String token,
            LocalDate expiry,
            BigDecimal strike,
            OptionType optionType
    ) {
        boolean matches(String requestedUnderlying, LocalDate requestedExpiry, BigDecimal requestedStrike, OptionType requestedOptionType) {
            return underlying.equalsIgnoreCase(requestedUnderlying)
                    && expiry.equals(requestedExpiry)
                    && strike.compareTo(requestedStrike.setScale(2, RoundingMode.HALF_UP)) == 0
                    && optionType == requestedOptionType;
        }
    }
}
