package com.example.demo.service;

import com.example.demo.domain.InstrumentType;
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
            InstrumentType instrument,
            String symbol,
            String token,
            BigDecimal lastTradedPrice,
            Long volume,
            Long openInterest,
            Instant tickTime
    ) {
        MarketTickEntity entity = new MarketTickEntity();
        entity.setInstrument(instrument);
        entity.setSymbol(symbol);
        entity.setToken(token);
        entity.setLastTradedPrice(lastTradedPrice);
        entity.setVolume(volume);
        entity.setOpenInterest(openInterest);
        entity.setTickTime(tickTime == null ? Instant.now() : tickTime);
        MarketTickDto dto = mapperService.toDto(marketTickRepository.save(entity));
        cacheService.cacheTick(dto);
        return dto;
    }
}
