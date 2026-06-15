package com.example.demo.service;

import com.example.demo.config.AngelOneProperties;
import com.example.demo.domain.AuthSessionEntity;
import com.example.demo.domain.OptionSnapshotEntity;
import com.example.demo.domain.OptionType;
import com.example.demo.dto.OptionChainResponse;
import com.example.demo.dto.OptionSnapshotDto;
import com.example.demo.exception.SmartApiException;
import com.example.demo.repository.OptionSnapshotRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OptionChainService {

    private static final Logger log = LoggerFactory.getLogger(OptionChainService.class);
    private static final DateTimeFormatter SMART_API_EXPIRY = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("ddMMMyyyy")
            .toFormatter(Locale.ENGLISH);
    private static final int QUOTE_BATCH_SIZE = 50;

    private final AngelOneProperties properties;
    private final SmartApiClient smartApiClient;
    private final AuthService authService;
    private final OptionSnapshotRepository optionSnapshotRepository;
    private final CacheService cacheService;
    private final MapperService mapperService;
    private final ScripMasterService scripMasterService;

    public OptionChainService(
            AngelOneProperties properties,
            SmartApiClient smartApiClient,
            AuthService authService,
            OptionSnapshotRepository optionSnapshotRepository,
            CacheService cacheService,
            MapperService mapperService,
            ScripMasterService scripMasterService
    ) {
        this.properties = properties;
        this.smartApiClient = smartApiClient;
        this.authService = authService;
        this.optionSnapshotRepository = optionSnapshotRepository;
        this.cacheService = cacheService;
        this.mapperService = mapperService;
        this.scripMasterService = scripMasterService;
    }

    @Transactional
    public OptionChainResponse fetchAndStore(String stockName, String requestedExpiry) {
        String normalizedStockName = normalize(stockName);
        AuthSessionEntity session = authService.requireSession();
        String expiry = resolveExpiry(normalizedStockName, requestedExpiry);
        JsonNode response = smartApiClient.optionGreek(session.getJwtToken(), normalizedStockName, expiry);
        JsonNode data = response.path("data");
        if (!data.isArray()) {
            throw new SmartApiException("SmartAPI option chain response did not include a data array");
        }

        Instant now = Instant.now();
        LocalDate expiryDate = parseExpiry(expiry);
        List<OptionSnapshotEntity> entities = new ArrayList<>();
        boolean tokenResolutionAvailable = true;
        for (JsonNode item : data) {
            OptionSnapshotEntity entity = toEntity(normalizedStockName, item, expiryDate, now);
            if (entity != null) {
                if (tokenResolutionAvailable) {
                    try {
                        applyContractToken(entity);
                    } catch (RuntimeException ex) {
                        tokenResolutionAvailable = false;
                        log.warn("NFO option token resolution failed; using Option Greeks data only: {}", ex.getMessage());
                    }
                }
                entities.add(entity);
            }
        }

        enrichWithFullQuotes(session.getJwtToken(), entities);

        List<OptionSnapshotDto> snapshots = new ArrayList<>();
        for (OptionSnapshotEntity entity : entities) {
            snapshots.add(mapperService.toDto(optionSnapshotRepository.save(entity)));
        }
        cacheService.cacheOptionChain(normalizedStockName, snapshots);
        return new OptionChainResponse(normalizedStockName, now, snapshots);
    }

    @Transactional(readOnly = true)
    public OptionChainResponse latest(String stockName) {
        String normalizedStockName = normalize(stockName);
        List<OptionSnapshotDto> cached = cacheService.getOptionChain(normalizedStockName).orElse(null);
        if (cached != null && !cached.isEmpty()) {
            return new OptionChainResponse(normalizedStockName, cached.get(0).snapshotTime(), cached);
        }
        Instant since = Instant.now().minusSeconds(300);
        List<OptionSnapshotDto> snapshots = optionSnapshotRepository
                .findByStockNameAndSnapshotTimeAfterOrderByStrikeAscOptionTypeAsc(normalizedStockName, since)
                .stream()
                .map(mapperService::toDto)
                .toList();
        return new OptionChainResponse(normalizedStockName, Instant.now(), snapshots);
    }

    private OptionSnapshotEntity toEntity(String stockName, JsonNode item, LocalDate expiry, Instant now) {
        OptionType optionType = optionType(item);
        BigDecimal strike = decimal(item, "strikePrice", "strike", "strike_price");
        if (optionType == null || strike == null) {
            return null;
        }

        OptionSnapshotEntity entity = new OptionSnapshotEntity();
        entity.setStockName(stockName);
        entity.setSymbol(text(item, "tradingSymbol", "tradingsymbol", "symbol", "name"));
        entity.setToken(text(item, "symbolToken", "symboltoken", "token"));
        entity.setStrike(strike);
        entity.setOptionType(optionType);
        entity.setExpiry(expiry);
        entity.setLastTradedPrice(decimal(item, "ltp", "lastTradedPrice", "close"));
        entity.setVolume(longValue(item, "volume", "tradeVolume", "totalTradedVolume"));
        entity.setOpenInterest(longValue(item, "openInterest", "opnInterest", "oi"));
        entity.setOiChange(longValue(item, "changeinOpenInterest", "oiChange", "changeInOI"));
        entity.setVwap(decimal(item, "vwap", "averagePrice"));
        entity.setPriceChange(decimal(item, "netChange", "priceChange", "change"));
        entity.setSnapshotTime(now);
        if (entity.getSymbol() == null || entity.getSymbol().isBlank()) {
            entity.setSymbol(stockName + expiry + strike + optionType.name());
        }
        if (entity.getToken() == null || entity.getToken().isBlank()) {
            entity.setToken(entity.getSymbol());
        }
        return entity;
    }

    private void applyContractToken(OptionSnapshotEntity entity) {
        scripMasterService.findOptionContract(
                        entity.getStockName(),
                        entity.getExpiry(),
                        entity.getStrike(),
                        entity.getOptionType()
                )
                .ifPresent(contract -> {
                    entity.setSymbol(contract.symbol());
                    entity.setToken(contract.token());
                });
    }

    private void enrichWithFullQuotes(String jwtToken, List<OptionSnapshotEntity> entities) {
        List<String> tokens = entities.stream()
                .map(OptionSnapshotEntity::getToken)
                .filter(token -> token != null && token.matches("\\d+"))
                .distinct()
                .toList();
        if (tokens.isEmpty()) {
            log.warn("No NFO option tokens resolved; using Option Greeks data only");
            return;
        }

        Map<String, JsonNode> quoteByToken = new HashMap<>();
        for (int index = 0; index < tokens.size(); index += QUOTE_BATCH_SIZE) {
            List<String> batch = tokens.subList(index, Math.min(index + QUOTE_BATCH_SIZE, tokens.size()));
            try {
                JsonNode response = smartApiClient.fullMarketQuote(jwtToken, "NFO", batch);
                JsonNode fetched = response.path("data").path("fetched");
                if (fetched.isArray()) {
                    for (JsonNode quote : fetched) {
                        String token = text(quote, "symbolToken", "symboltoken", "token");
                        if (token != null) {
                            quoteByToken.put(token, quote);
                        }
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("Angel One FULL quote batch failed: {}", ex.getMessage());
            }
        }

        for (OptionSnapshotEntity entity : entities) {
            JsonNode quote = quoteByToken.get(entity.getToken());
            if (quote != null) {
                applyQuote(entity, quote);
            }
        }
    }

    private void applyQuote(OptionSnapshotEntity entity, JsonNode quote) {
        BigDecimal lastTradedPrice = decimal(quote, "ltp", "lastTradedPrice", "last_traded_price");
        Long volume = longValue(quote, "tradeVolume", "volume", "totalTradedVolume");
        Long openInterest = longValue(quote, "opnInterest", "openInterest", "oi");
        Long oiChange = longValue(quote, "netChangeOpnInterest", "changeinOpenInterest", "oiChange", "changeInOI");
        BigDecimal vwap = decimal(quote, "avgPrice", "averagePrice", "vwap");
        BigDecimal priceChange = decimal(quote, "netChange", "priceChange", "change");
        String symbol = text(quote, "tradingSymbol", "tradingsymbol", "symbol");

        if (lastTradedPrice != null) {
            entity.setLastTradedPrice(lastTradedPrice);
        }
        if (volume != null) {
            entity.setVolume(volume);
        }
        if (openInterest != null) {
            entity.setOpenInterest(openInterest);
        }
        if (oiChange != null) {
            entity.setOiChange(oiChange);
        }
        if (vwap != null) {
            entity.setVwap(vwap);
        }
        if (priceChange != null) {
            entity.setPriceChange(priceChange);
        }
        if (symbol != null) {
            entity.setSymbol(symbol);
        }
    }

    private String resolveExpiry(String stockName, String requestedExpiry) {
        String expiry = requestedExpiry;
        if (expiry == null || expiry.isBlank()) {
            expiry = switch (stockName) {
                case "NIFTY" -> properties.optionChain().niftyExpiry();
                case "BANKNIFTY" -> properties.optionChain().bankniftyExpiry();
                default -> null;
            };
        }
        if (expiry == null || expiry.isBlank()) {
            throw new SmartApiException("Provide expiry for " + stockName + " using ?expiry=27JUN2026 or configure NIFTY_EXPIRY/BANKNIFTY_EXPIRY");
        }
        return expiry.toUpperCase(Locale.ENGLISH);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new SmartApiException("Stock name is required");
        }
        return value.trim().toUpperCase(Locale.ENGLISH);
    }

    private LocalDate parseExpiry(String expiry) {
        try {
            return LocalDate.parse(expiry, SMART_API_EXPIRY);
        } catch (DateTimeParseException ex) {
            throw new SmartApiException("Expiry must use Angel One format ddMMMyyyy, for example 27JUN2026");
        }
    }

    private OptionType optionType(JsonNode node) {
        String value = text(node, "optionType", "option_type", "instrumentType");
        if (value == null) {
            String symbol = text(node, "tradingSymbol", "tradingsymbol", "symbol");
            value = symbol == null ? null : symbol.substring(Math.max(0, symbol.length() - 2));
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

    private Long longValue(JsonNode node, String... fields) {
        String value = text(node, fields);
        return value == null ? null : new BigDecimal(value.replace(",", "")).longValue();
    }
}
