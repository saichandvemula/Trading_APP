package com.example.demo.service;

import com.example.demo.domain.MarketTickEntity;
import com.example.demo.domain.OptionSnapshotEntity;
import com.example.demo.domain.TradingSignalEntity;
import com.example.demo.dto.MarketTickDto;
import com.example.demo.dto.OptionSnapshotDto;
import com.example.demo.dto.TradingSignalDto;
import org.springframework.stereotype.Component;

@Component
public class MapperService {

    public MarketTickDto toDto(MarketTickEntity entity) {
        return new MarketTickDto(
                entity.getInstrument(),
                entity.getSymbol(),
                entity.getToken(),
                entity.getLastTradedPrice(),
                entity.getVolume(),
                entity.getOpenInterest(),
                entity.getTickTime()
        );
    }

    public OptionSnapshotDto toDto(OptionSnapshotEntity entity) {
        return new OptionSnapshotDto(
                entity.getInstrument(),
                entity.getSymbol(),
                entity.getToken(),
                entity.getStrike(),
                entity.getOptionType(),
                entity.getExpiry(),
                entity.getLastTradedPrice(),
                entity.getVolume(),
                entity.getOpenInterest(),
                entity.getOiChange(),
                entity.getVwap(),
                entity.getPriceChange(),
                entity.getSnapshotTime()
        );
    }

    public TradingSignalDto toDto(TradingSignalEntity entity) {
        return new TradingSignalDto(
                entity.getInstrument(),
                entity.getDirection(),
                entity.getConfidence(),
                entity.getReason(),
                entity.getPcr(),
                entity.getCePriceMovement(),
                entity.getPePriceMovement(),
                entity.isVolumeSpike(),
                entity.getGeneratedAt()
        );
    }
}
