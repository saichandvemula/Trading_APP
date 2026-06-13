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
@Table(name = "trading_signals", indexes = @Index(name = "idx_trading_signals_stock_name_time", columnList = "stock_name,generated_at"))
public class TradingSignalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_name", nullable = false, length = 64)
    private String stockName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SignalDirection direction;

    @Column(nullable = false)
    private int confidence;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(precision = 10, scale = 4)
    private BigDecimal pcr;

    @Column(name = "ce_price_movement", precision = 19, scale = 4)
    private BigDecimal cePriceMovement;

    @Column(name = "pe_price_movement", precision = 19, scale = 4)
    private BigDecimal pePriceMovement;

    @Column(name = "volume_spike")
    private boolean volumeSpike;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStockName() {
        return stockName;
    }

    public void setStockName(String stockName) {
        this.stockName = stockName;
    }

    public SignalDirection getDirection() {
        return direction;
    }

    public void setDirection(SignalDirection direction) {
        this.direction = direction;
    }

    public int getConfidence() {
        return confidence;
    }

    public void setConfidence(int confidence) {
        this.confidence = confidence;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public BigDecimal getPcr() {
        return pcr;
    }

    public void setPcr(BigDecimal pcr) {
        this.pcr = pcr;
    }

    public BigDecimal getCePriceMovement() {
        return cePriceMovement;
    }

    public void setCePriceMovement(BigDecimal cePriceMovement) {
        this.cePriceMovement = cePriceMovement;
    }

    public BigDecimal getPePriceMovement() {
        return pePriceMovement;
    }

    public void setPePriceMovement(BigDecimal pePriceMovement) {
        this.pePriceMovement = pePriceMovement;
    }

    public boolean isVolumeSpike() {
        return volumeSpike;
    }

    public void setVolumeSpike(boolean volumeSpike) {
        this.volumeSpike = volumeSpike;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }
}
