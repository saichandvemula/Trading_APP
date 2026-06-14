package com.example.demo.controller;

import com.example.demo.dto.Nifty50WeeklyPredictionResponse;
import com.example.demo.dto.WeeklyPredictionResponse;
import com.example.demo.service.WeeklyPredictionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weekly-prediction")
public class WeeklyPredictionController {

    private final WeeklyPredictionService weeklyPredictionService;

    public WeeklyPredictionController(WeeklyPredictionService weeklyPredictionService) {
        this.weeklyPredictionService = weeklyPredictionService;
    }

    @GetMapping("/nifty50")
    public Nifty50WeeklyPredictionResponse predictNifty50() {
        return weeklyPredictionService.predictNifty50();
    }

    @PostMapping("/nifty50")
    public Nifty50WeeklyPredictionResponse predictNifty50Post() {
        return weeklyPredictionService.predictNifty50();
    }

    /*
     * Uses past daily historical candles to suggest next-week movement.
     * NIFTY and BANKNIFTY tokens are resolved automatically.
     * For other symbols, pass the Angel One symbol token:
     * GET /api/weekly-prediction/RELIANCE?symbolToken=2885
     */
    @GetMapping("/{stockName}")
    public WeeklyPredictionResponse predict(
            @PathVariable String stockName,
            @RequestParam(required = false, defaultValue = "NSE") String exchange,
            @RequestParam(required = false) String symbolToken
    ) {
        return weeklyPredictionService.predict(stockName, exchange, symbolToken);
    }

    @PostMapping("/{stockName}")
    public WeeklyPredictionResponse predictPost(
            @PathVariable String stockName,
            @RequestParam(required = false, defaultValue = "NSE") String exchange,
            @RequestParam(required = false) String symbolToken
    ) {
        return weeklyPredictionService.predict(stockName, exchange, symbolToken);
    }
}
