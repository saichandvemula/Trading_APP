package com.example.demo.service;

import com.example.demo.config.TradingProperties;
import com.example.demo.domain.InstrumentType;
import com.example.demo.domain.SignalDirection;
import com.example.demo.domain.TradingSignalEntity;
import com.example.demo.dto.IndicatorSummaryDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PredictionEngine {

    private final TradingProperties properties;

    public PredictionEngine(TradingProperties properties) {
        this.properties = properties;
    }

    public TradingSignalEntity predict(InstrumentType instrument, IndicatorSummaryDto indicators) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        int pcrCompare = indicators.pcr().compareTo(BigDecimal.ONE);
        if (pcrCompare > 0) {
            score += 25;
            reasons.add("PCR above 1.0 shows stronger put OI support");
        } else if (pcrCompare < 0) {
            score -= 25;
            reasons.add("PCR below 1.0 shows stronger call OI resistance");
        }

        long oiChangeDelta = indicators.totalPutOiChange() - indicators.totalCallOiChange();
        if (oiChangeDelta > 0) {
            score += 25;
            reasons.add("Put OI change is stronger than call OI change");
        } else if (oiChangeDelta < 0) {
            score -= 25;
            reasons.add("Call OI change is stronger than put OI change");
        }

        if (indicators.callPriceMovement().compareTo(indicators.putPriceMovement()) > 0) {
            score += 20;
            reasons.add("CE price movement is stronger than PE movement");
        } else if (indicators.putPriceMovement().compareTo(indicators.callPriceMovement()) > 0) {
            score -= 20;
            reasons.add("PE price movement is stronger than CE movement");
        }

        if (indicators.volumeSpike()) {
            score += score >= 0 ? 10 : -10;
            reasons.add("Volume spike confirms directional participation");
        }

        SignalDirection direction;
        int absoluteScore = Math.min(Math.abs(score), 100);
        if (absoluteScore < properties.sidewaysScoreThreshold()) {
            direction = SignalDirection.SIDEWAYS;
        } else if (absoluteScore < properties.minConfidence()) {
            direction = SignalDirection.NO_TRADE;
        } else {
            direction = score > 0 ? SignalDirection.BULLISH : SignalDirection.BEARISH;
        }

        TradingSignalEntity entity = new TradingSignalEntity();
        entity.setInstrument(instrument);
        entity.setDirection(direction);
        entity.setConfidence(absoluteScore);
        entity.setReason(reasons.isEmpty() ? "Insufficient fresh option-chain evidence" : String.join("; ", reasons));
        entity.setPcr(indicators.pcr());
        entity.setCePriceMovement(indicators.callPriceMovement());
        entity.setPePriceMovement(indicators.putPriceMovement());
        entity.setVolumeSpike(indicators.volumeSpike());
        entity.setGeneratedAt(Instant.now());
        return entity;
    }
}
