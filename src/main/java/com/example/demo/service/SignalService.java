package com.example.demo.service;

import com.example.demo.domain.InstrumentType;
import com.example.demo.domain.TradingSignalEntity;
import com.example.demo.dto.MarketDirectionResponse;
import com.example.demo.dto.TradingSignalDto;
import com.example.demo.repository.TradingSignalRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignalService {

    private final IndicatorService indicatorService;
    private final PredictionEngine predictionEngine;
    private final TradingSignalRepository tradingSignalRepository;
    private final MapperService mapperService;

    public SignalService(
            IndicatorService indicatorService,
            PredictionEngine predictionEngine,
            TradingSignalRepository tradingSignalRepository,
            MapperService mapperService
    ) {
        this.indicatorService = indicatorService;
        this.predictionEngine = predictionEngine;
        this.tradingSignalRepository = tradingSignalRepository;
        this.mapperService = mapperService;
    }

    @Transactional
    public TradingSignalDto process(InstrumentType instrument) {
        TradingSignalEntity signal = predictionEngine.predict(instrument, indicatorService.summarize(instrument));
        return mapperService.toDto(tradingSignalRepository.save(signal));
    }

    @Transactional
    public List<TradingSignalDto> processAll() {
        return Arrays.stream(InstrumentType.values()).map(this::process).toList();
    }

    @Transactional
    public TradingSignalDto latestSignal(InstrumentType instrument) {
        return tradingSignalRepository.findTopByInstrumentOrderByGeneratedAtDesc(instrument)
                .map(mapperService::toDto)
                .orElseGet(() -> process(instrument));
    }

    @Transactional
    public MarketDirectionResponse direction(InstrumentType instrument) {
        TradingSignalDto signal = latestSignal(instrument);
        return new MarketDirectionResponse(
                signal.instrument(),
                signal.direction(),
                signal.confidence(),
                signal.reason(),
                signal.generatedAt()
        );
    }
}
