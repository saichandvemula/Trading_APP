package com.example.demo.service;

import com.example.demo.config.AngelOneProperties;
import com.example.demo.domain.AuthSessionEntity;
import com.example.demo.domain.InstrumentType;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OptionChainService {

    private static final DateTimeFormatter SMART_API_EXPIRY = DateTimeFormatter.ofPattern("ddMMMyyyy", Locale.ENGLISH);

    private final AngelOneProperties properties;
    private final SmartApiClient smartApiClient;
    private final AuthService authService;
    private final OptionSnapshotRepository optionSnapshotRepository;
    private final CacheService cacheService;
    private final MapperService mapperService;

    public OptionChainService(
            AngelOneProperties properties,
            SmartApiClient smartApiClient,
            AuthService authService,
            OptionSnapshotRepository optionSnapshotRepository,
            CacheService cacheService,
            MapperService mapperService
    ) {
        this.properties = properties;
        this.smartApiClient = smartApiClient;
        this.authService = authService;
        this.optionSnapshotRepository = optionSnapshotRepository;
        this.cacheService = cacheService;
        this.mapperService = mapperService;
    }

    @Transactional
    public OptionChainResponse fetchAndStore(InstrumentType instrument) {
        AuthSessionEntity session = authService.requireSession();
        String expiry = configuredExpiry(instrument);
        JsonNode response = smartApiClient.optionGreek(session.getJwtToken(), instrument.name(), expiry);
        JsonNode data = response.path("data");
        if (!data.isArray()) {
            throw new SmartApiException("SmartAPI option chain response did not include a data array");
        }

        Instant now = Instant.now();
        LocalDate expiryDate = parseExpiry(expiry);
        List<OptionSnapshotDto> snapshots = new ArrayList<>();
        for (JsonNode item : data) {
            OptionSnapshotEntity entity = toEntity(instrument, item, expiryDate, now);
            if (entity != null) {
                snapshots.add(mapperService.toDto(optionSnapshotRepository.save(entity)));
            }
        }
        cacheService.cacheOptionChain(instrument.name(), snapshots);
        return new OptionChainResponse(instrument, now, snapshots);
    }

    @Transactional(readOnly = true)
    public OptionChainResponse latest(InstrumentType instrument) {
        List<OptionSnapshotDto> cached = cacheService.getOptionChain(instrument.name()).orElse(null);
        if (cached != null && !cached.isEmpty()) {
            return new OptionChainResponse(instrument, cached.get(0).snapshotTime(), cached);
        }
        Instant since = Instant.now().minusSeconds(300);
        List<OptionSnapshotDto> snapshots = optionSnapshotRepository
                .findByInstrumentAndSnapshotTimeAfterOrderByStrikeAscOptionTypeAsc(instrument, since)
                .stream()
                .map(mapperService::toDto)
                .toList();
        return new OptionChainResponse(instrument, Instant.now(), snapshots);
    }

    private OptionSnapshotEntity toEntity(InstrumentType instrument, JsonNode item, LocalDate expiry, Instant now) {
        OptionType optionType = optionType(item);
        BigDecimal strike = decimal(item, "strikePrice", "strike", "strike_price");
        if (optionType == null || strike == null) {
            return null;
        }

        OptionSnapshotEntity entity = new OptionSnapshotEntity();
        entity.setInstrument(instrument);
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
            entity.setSymbol(instrument.name() + expiry + strike + optionType.name());
        }
        if (entity.getToken() == null || entity.getToken().isBlank()) {
            entity.setToken(entity.getSymbol());
        }
        return entity;
    }

    private String configuredExpiry(InstrumentType instrument) {
        String expiry = instrument == InstrumentType.NIFTY
                ? properties.optionChain().niftyExpiry()
                : properties.optionChain().bankniftyExpiry();
        if (expiry == null || expiry.isBlank()) {
            throw new SmartApiException("Configure " + instrument + " expiry using NIFTY_EXPIRY or BANKNIFTY_EXPIRY, for example 27JUN2026");
        }
        return expiry.toUpperCase(Locale.ENGLISH);
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
