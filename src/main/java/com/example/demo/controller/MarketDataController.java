package com.example.demo.controller;

import com.example.demo.domain.InstrumentType;
import com.example.demo.dto.IndicatorSummaryDto;
import com.example.demo.dto.MarketDirectionResponse;
import com.example.demo.dto.OptionChainResponse;
import com.example.demo.dto.TradingSignalDto;
import com.example.demo.service.AngelOneWebSocketService;
import com.example.demo.service.IndicatorService;
import com.example.demo.service.OptionChainService;
import com.example.demo.service.SignalService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketDataController {

    /*
     * This controller exposes market-data APIs.
     * It does not calculate indicators or talk to Angel One directly.
     * It only receives HTTP requests and delegates work to service classes.
     */

    private final OptionChainService optionChainService;
    private final SignalService signalService;
    private final IndicatorService indicatorService;
    private final AngelOneWebSocketService webSocketService;

    public MarketDataController(
            OptionChainService optionChainService,
            SignalService signalService,
            IndicatorService indicatorService,
            AngelOneWebSocketService webSocketService
    ) {
        this.optionChainService = optionChainService;
        this.signalService = signalService;
        this.indicatorService = indicatorService;
        this.webSocketService = webSocketService;
    }

    /*
     * Starts the Angel One WebSocket connection manually.
     * Use this after a successful Angel One login if the socket was not connected during app startup.
     */
    @PostMapping("/websocket/connect")
    public Map<String, Boolean> connectWebSocket() {
        return Map.of("connected", webSocketService.connect());
    }

    /*
     * Returns the current WebSocket connection status.
     * Response example: {"connected": true}
     */
    @GetMapping("/websocket/status")
    public Map<String, Boolean> webSocketStatus() {
        return Map.of("connected", webSocketService.isConnected());
    }

    /*
     * Fetches fresh option-chain data from Angel One SmartAPI for NIFTY or BANKNIFTY.
     * The data is saved into H2 and also cached for quick reads.
     */
    @PostMapping("/{instrument}/option-chain/refresh")
    public OptionChainResponse refreshOptionChain(@PathVariable InstrumentType instrument) {
        return optionChainService.fetchAndStore(instrument);
    }

    /*
     * Returns the latest option-chain snapshot for the requested instrument.
     * It reads from Redis/in-memory cache first and falls back to recent H2 records.
     */
    @GetMapping("/{instrument}/option-chain")
    public OptionChainResponse optionChain(@PathVariable InstrumentType instrument) {
        return optionChainService.latest(instrument);
    }

    /*
     * Calculates indicator values from the latest option-chain snapshots.
     * Includes PCR, OI change, VWAP, volume spike, and CE/PE price movement.
     */
    @GetMapping("/{instrument}/indicators")
    public IndicatorSummaryDto indicators(@PathVariable InstrumentType instrument) {
        return indicatorService.summarize(instrument);
    }

    /*
     * Returns the latest market direction for the requested instrument.
     * Direction can be BULLISH, BEARISH, SIDEWAYS, or NO_TRADE.
     */
    @GetMapping("/{instrument}/direction")
    public MarketDirectionResponse direction(@PathVariable InstrumentType instrument) {
        return signalService.direction(instrument);
    }

    /*
     * Runs the rule-based signal engine immediately for one instrument.
     * The generated signal is saved in H2 with confidence and reason.
     */
    @PostMapping("/{instrument}/signals/process")
    public TradingSignalDto processSignal(@PathVariable InstrumentType instrument) {
        return signalService.process(instrument);
    }

    /*
     * Returns the most recent saved trading signal for one instrument.
     * If no signal exists yet, the service generates one and returns it.
     */
    @GetMapping("/{instrument}/signals/latest")
    public TradingSignalDto latestSignal(@PathVariable InstrumentType instrument) {
        return signalService.latestSignal(instrument);
    }

    /*
     * Runs signal processing for all configured instruments.
     * Currently this processes both NIFTY and BANKNIFTY.
     */
    @PostMapping("/signals/process")
    public List<TradingSignalDto> processAllSignals() {
        return signalService.processAll();
    }
}
