package com.example.demo.service;

import com.example.demo.domain.MarketTickEntity;
import com.example.demo.dto.MarketTickDto;
import com.example.demo.repository.MarketTickRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketDataService {

    private final MarketTickRepository marketTickRepository;
    private final CacheService cacheService;
    private final MapperService mapperService;

    public MarketDataService(MarketTickRepository marketTickRepository, CacheService cacheService, MapperService mapperService) {
        this.marketTickRepository = marketTickRepository;
        this.cacheService = cacheService;
        this.mapperService = mapperService;
    }

    @Transactional
    public MarketTickDto saveTick(
            String symbol,
            String token,
            BigDecimal lastTradedPrice,
            Long volume,
            Long openInterest,
            Instant tickTime
    ) {
        String normalizedSymbol = normalize(symbol);
        MarketTickEntity entity = new MarketTickEntity();
        entity.setStockName(normalizedSymbol);
        entity.setSymbol(normalizedSymbol);
        entity.setToken(token);
        entity.setLastTradedPrice(lastTradedPrice);
        entity.setVolume(volume);
        entity.setOpenInterest(openInterest);
        entity.setTickTime(tickTime == null ? Instant.now() : tickTime);
        MarketTickDto dto = mapperService.toDto(marketTickRepository.save(entity));
        cacheService.cacheTick(dto);
        return dto;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}
