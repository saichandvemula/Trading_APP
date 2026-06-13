package com.example.demo.service;

import com.example.demo.domain.TradingSignalEntity;
import com.example.demo.dto.MarketDirectionResponse;
import com.example.demo.dto.TradingSignalDto;
import com.example.demo.repository.TradingSignalRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignalService {

    private static final List<String> DEFAULT_STOCK_NAMES = List.of("NIFTY", "BANKNIFTY");

    private final IndicatorService indicatorService;
    private final PredictionEngine predictionEngine;
    private final TradingSignalRepository tradingSignalRepository;
    private final MapperService mapperService;
    private final OptionChainService optionChainService;

    public SignalService(
            IndicatorService indicatorService,
            PredictionEngine predictionEngine,
            TradingSignalRepository tradingSignalRepository,
            MapperService mapperService,
            OptionChainService optionChainService
    ) {
        this.indicatorService = indicatorService;
        this.predictionEngine = predictionEngine;
        this.tradingSignalRepository = tradingSignalRepository;
        this.mapperService = mapperService;
        this.optionChainService = optionChainService;
    }

    @Transactional
    public TradingSignalDto process(String stockName) {
        String normalizedStockName = normalize(stockName);
        TradingSignalEntity signal = predictionEngine.predict(normalizedStockName, indicatorService.summarize(normalizedStockName));
        return mapperService.toDto(tradingSignalRepository.save(signal));
    }

    @Transactional
    public TradingSignalDto refreshOptionChainAndProcess(String stockName, String expiry) {
        String normalizedStockName = normalize(stockName);
        optionChainService.fetchAndStore(normalizedStockName, expiry);
        return process(normalizedStockName);
    }

    @Transactional
    public List<TradingSignalDto> processAll() {
        return DEFAULT_STOCK_NAMES.stream().map(this::process).toList();
    }

    @Transactional
    public TradingSignalDto latestSignal(String stockName) {
        String normalizedStockName = normalize(stockName);
        return tradingSignalRepository.findTopByStockNameOrderByGeneratedAtDesc(normalizedStockName)
                .map(mapperService::toDto)
                .orElseGet(() -> process(normalizedStockName));
    }

    @Transactional
    public MarketDirectionResponse direction(String stockName) {
        TradingSignalDto signal = latestSignal(stockName);
        return new MarketDirectionResponse(
                signal.stockName(),
                signal.direction(),
                signal.confidence(),
                signal.reason(),
                signal.generatedAt()
        );
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}
