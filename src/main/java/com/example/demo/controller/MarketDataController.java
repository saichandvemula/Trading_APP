package com.example.demo.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
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
     * Fetches fresh option-chain data from Angel One SmartAPI for any stock/index symbol.
     * For NIFTY and BANKNIFTY, expiry can come from application.yml.
     * For other symbols, pass expiry in the URL, for example:
     * POST /api/market/RELIANCE/option-chain/refresh?expiry=27JUN2026
     * The data is saved into H2 and also cached for quick reads.
     */
    @PostMapping("/{stockName}/option-chain/refresh")
    public OptionChainResponse refreshOptionChain(
            @PathVariable String stockName,
            @RequestParam(required = false) String expiry
    ) {
        return optionChainService.fetchAndStore(stockName, expiry);
    }

    /*
     * Returns the latest option-chain snapshot for the requested stock name.
     * It reads from Redis/in-memory cache first and falls back to recent H2 records.
     */
    @GetMapping("/{stockName}/option-chain")
    public OptionChainResponse optionChain(@PathVariable String stockName) {
        return optionChainService.latest(stockName);
    }

    /*
     * Calculates indicator values from the latest option-chain snapshots.
     * Includes PCR, OI change, VWAP, volume spike, and CE/PE price movement.
     */
    @GetMapping("/{stockName}/indicators")
    public IndicatorSummaryDto indicators(@PathVariable String stockName) {
        return indicatorService.summarize(stockName);
    }

    /*
     * Returns the latest market direction for the requested stock name.
     * Direction can be BULLISH, BEARISH, SIDEWAYS, or NO_TRADE.
     */
    @GetMapping("/{stockName}/direction")
    public MarketDirectionResponse direction(@PathVariable String stockName) {
        return signalService.direction(stockName);
    }

    /*
     * Runs the rule-based signal engine immediately for one stock name.
     * The generated signal is saved in H2 with confidence and reason.
     */
    @PostMapping("/{stockName}/signals/process")
    public TradingSignalDto processSignal(@PathVariable String stockName) {
        return signalService.process(stockName);
    }

    /*
     * One-step signal API.
     * This first fetches the latest option-chain data from Angel One for the stock name,
     * stores the option snapshots, calculates indicators, and returns the final signal.
     *
     * Example:
     * POST /api/market/RELIANCE/signal?expiry=27JUN2026
     */
    @PostMapping("/{stockName}/signal")
    public TradingSignalDto refreshOptionChainAndGetSignal(
            @PathVariable String stockName,
            @RequestParam(required = false) String expiry
    ) {
        return signalService.refreshOptionChainAndProcess(stockName, expiry);
    }

    /*
     * Returns the most recent saved trading signal for one stock name.
     * If no signal exists yet, the service generates one and returns it.
     */
    @GetMapping("/{stockName}/signals/latest")
    public TradingSignalDto latestSignal(@PathVariable String stockName) {
        return signalService.latestSignal(stockName);
    }

    /*
     * Runs signal processing for default configured stock names.
     * Currently this processes both NIFTY and BANKNIFTY.
     */
    @PostMapping("/signals/process")
    public List<TradingSignalDto> processAllSignals() {
        return signalService.processAll();
    }
}
