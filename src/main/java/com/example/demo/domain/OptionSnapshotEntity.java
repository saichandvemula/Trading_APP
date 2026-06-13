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
import java.time.LocalDate;

@Entity
@Table(name = "option_snapshots", indexes = {
        @Index(name = "idx_option_snapshots_instrument_time", columnList = "instrument,snapshot_time"),
        @Index(name = "idx_option_snapshots_symbol_time", columnList = "symbol,snapshot_time")
})
public class OptionSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InstrumentType instrument;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String token;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal strike;

    @Enumerated(EnumType.STRING)
    @Column(name = "option_type", nullable = false, length = 2)
    private OptionType optionType;

    @Column(nullable = false)
    private LocalDate expiry;

    @Column(name = "last_traded_price", precision = 19, scale = 4)
    private BigDecimal lastTradedPrice;

    @Column
    private Long volume;

    @Column(name = "open_interest")
    private Long openInterest;

    @Column(name = "oi_change")
    private Long oiChange;

    @Column(precision = 19, scale = 4)
    private BigDecimal vwap;

    @Column(name = "price_change", precision = 19, scale = 4)
    private BigDecimal priceChange;

    @Column(name = "snapshot_time", nullable = false)
    private Instant snapshotTime;

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

    public BigDecimal getStrike() {
        return strike;
    }

    public void setStrike(BigDecimal strike) {
        this.strike = strike;
    }

    public OptionType getOptionType() {
        return optionType;
    }

    public void setOptionType(OptionType optionType) {
        this.optionType = optionType;
    }

    public LocalDate getExpiry() {
        return expiry;
    }

    public void setExpiry(LocalDate expiry) {
        this.expiry = expiry;
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

    public Long getOiChange() {
        return oiChange;
    }

    public void setOiChange(Long oiChange) {
        this.oiChange = oiChange;
    }

    public BigDecimal getVwap() {
        return vwap;
    }

    public void setVwap(BigDecimal vwap) {
        this.vwap = vwap;
    }

    public BigDecimal getPriceChange() {
        return priceChange;
    }

    public void setPriceChange(BigDecimal priceChange) {
        this.priceChange = priceChange;
    }

    public Instant getSnapshotTime() {
        return snapshotTime;
    }

    public void setSnapshotTime(Instant snapshotTime) {
        this.snapshotTime = snapshotTime;
    }
}
