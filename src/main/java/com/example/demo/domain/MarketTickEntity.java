package com.example.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "market_ticks", indexes = {
        @Index(name = "idx_market_ticks_symbol_time", columnList = "symbol,tick_time"),
        @Index(name = "idx_market_ticks_instrument_time", columnList = "instrument,tick_time")
})
public class MarketTickEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InstrumentType instrument;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "token", nullable = false)
    private String token;

    @Column(name = "last_traded_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal lastTradedPrice;

    @Column(name = "volume")
    private Long volume;

    @Column(name = "open_interest")
    private Long openInterest;

    @Column(name = "tick_time", nullable = false)
    private Instant tickTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public InstrumentType getInstrument() {
        return instrument;
    }

    public void setInstrument(InstrumentType instrument) {
        this.instrument = instrument;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public BigDecimal getLastTradedPrice() {
        return lastTradedPrice;
    }

    public void setLastTradedPrice(BigDecimal lastTradedPrice) {
        this.lastTradedPrice = lastTradedPrice;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }

    public Long getOpenInterest() {
        return openInterest;
    }

    public void setOpenInterest(Long openInterest) {
        this.openInterest = openInterest;
    }

    public Instant getTickTime() {
        return tickTime;
    }

    public void setTickTime(Instant tickTime) {
        this.tickTime = tickTime;
    }
}
