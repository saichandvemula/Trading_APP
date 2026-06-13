package com.example.demo.service;

import com.example.demo.dto.MarketTickDto;
import com.example.demo.dto.OptionSnapshotDto;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);
    private static final Duration MARKET_TTL = Duration.ofMinutes(30);
    private static final Duration OPTION_CHAIN_TTL = Duration.ofMinutes(5);
    private final RedisTemplate<String, Object> redisTemplate;
    private final Map<String, Object> localCache = new ConcurrentHashMap<>();

    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void cacheTick(MarketTickDto tick) {
        set(tickKey(tick.symbol()), tick, MARKET_TTL);
        set(tokenKey(tick.token()), tick, MARKET_TTL);
    }

    public Optional<MarketTickDto> getTickBySymbol(String symbol) {
        Object value = get(tickKey(symbol));
        return value instanceof MarketTickDto tick ? Optional.of(tick) : Optional.empty();
    }

    public void cacheOptionChain(String stockName, List<OptionSnapshotDto> options) {
        set(optionChainKey(stockName), options, OPTION_CHAIN_TTL);
    }

    @SuppressWarnings("unchecked")
    public Optional<List<OptionSnapshotDto>> getOptionChain(String stockName) {
        Object value = get(optionChainKey(stockName));
        return value instanceof List<?> list ? Optional.of((List<OptionSnapshotDto>) list) : Optional.empty();
    }

    private void set(String key, Object value, Duration ttl) {
        localCache.put(key, value);
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (RedisConnectionFailureException ex) {
            log.debug("Redis unavailable; using in-memory cache for key {}", key);
        }
    }

    private Object get(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            return value == null ? localCache.get(key) : value;
        } catch (RedisConnectionFailureException ex) {
            log.debug("Redis unavailable; reading key {} from in-memory cache", key);
            return localCache.get(key);
        }
    }

    private String tickKey(String symbol) {
        return "market:tick:symbol:" + symbol;
    }

    private String tokenKey(String token) {
        return "market:tick:token:" + token;
    }

    private String optionChainKey(String stockName) {
        return "market:option-chain:" + stockName;
    }
}
