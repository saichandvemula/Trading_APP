package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SignalScheduler {

    private static final Logger log = LoggerFactory.getLogger(SignalScheduler.class);
    private final SignalService signalService;

    public SignalScheduler(SignalService signalService) {
        this.signalService = signalService;
    }

    @Scheduled(fixedRateString = "60000", initialDelayString = "15000")
    public void processSignals() {
        try {
            signalService.processAll();
        } catch (RuntimeException ex) {
            log.warn("Scheduled signal processing failed: {}", ex.getMessage());
        }
    }
}
