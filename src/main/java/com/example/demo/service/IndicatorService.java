package com.example.demo.service;

import com.example.demo.config.TradingProperties;
import com.example.demo.domain.OptionSnapshotEntity;
import com.example.demo.domain.OptionType;
import com.example.demo.dto.IndicatorSummaryDto;
import com.example.demo.repository.OptionSnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndicatorService {

    private final OptionSnapshotRepository optionSnapshotRepository;
    private final TradingProperties tradingProperties;

    public IndicatorService(OptionSnapshotRepository optionSnapshotRepository, TradingProperties tradingProperties) {
        this.optionSnapshotRepository = optionSnapshotRepository;
        this.tradingProperties = tradingProperties;
    }

    @Transactional(readOnly = true)
    public IndicatorSummaryDto summarize(String stockName) {
        List<OptionSnapshotEntity> snapshots = optionSnapshotRepository
                .findByStockNameAndSnapshotTimeAfterOrderByStrikeAscOptionTypeAsc(normalize(stockName), Instant.now().minusSeconds(300));

        long callOi = sumOi(snapshots, OptionType.CE);
        long putOi = sumOi(snapshots, OptionType.PE);
        long callOiChange = sumOiChange(snapshots, OptionType.CE);
        long putOiChange = sumOiChange(snapshots, OptionType.PE);
        long callPcrBase = callOi > 0 ? callOi : sumVolume(snapshots, OptionType.CE);
        long putPcrBase = putOi > 0 ? putOi : sumVolume(snapshots, OptionType.PE);
        BigDecimal pcr = callPcrBase == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(putPcrBase).divide(BigDecimal.valueOf(callPcrBase), 4, RoundingMode.HALF_UP);
        BigDecimal averageVwap = averageVwap(snapshots);
        BigDecimal callPriceMovement = averagePriceChange(snapshots, OptionType.CE);
        BigDecimal putPriceMovement = averagePriceChange(snapshots, OptionType.PE);
        boolean volumeSpike = isVolumeSpike(snapshots);
        return new IndicatorSummaryDto(
                pcr,
                callOi,
                putOi,
                callOiChange,
                putOiChange,
                averageVwap,
                callPriceMovement,
                putPriceMovement,
                volumeSpike
        );
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private long sumOi(List<OptionSnapshotEntity> snapshots, OptionType type) {
        return snapshots.stream()
                .filter(snapshot -> snapshot.getOptionType() == type)
                .map(OptionSnapshotEntity::getOpenInterest)
                .filter(value -> value != null)
                .mapToLong(Long::longValue)
                .sum();
    }

    private long sumOiChange(List<OptionSnapshotEntity> snapshots, OptionType type) {
        return snapshots.stream()
                .filter(snapshot -> snapshot.getOptionType() == type)
                .map(OptionSnapshotEntity::getOiChange)
                .filter(value -> value != null)
                .mapToLong(Long::longValue)
                .sum();
    }

    private long sumVolume(List<OptionSnapshotEntity> snapshots, OptionType type) {
        return snapshots.stream()
                .filter(snapshot -> snapshot.getOptionType() == type)
                .map(OptionSnapshotEntity::getVolume)
                .filter(value -> value != null)
                .mapToLong(Long::longValue)
                .sum();
    }

    private BigDecimal averagePriceChange(List<OptionSnapshotEntity> snapshots, OptionType type) {
        List<BigDecimal> values = snapshots.stream()
                .filter(snapshot -> snapshot.getOptionType() == type)
                .map(OptionSnapshotEntity::getPriceChange)
                .filter(value -> value != null)
                .toList();
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal averageVwap(List<OptionSnapshotEntity> snapshots) {
        List<BigDecimal> values = snapshots.stream()
                .map(OptionSnapshotEntity::getVwap)
                .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private boolean isVolumeSpike(List<OptionSnapshotEntity> snapshots) {
        List<Long> volumes = snapshots.stream()
                .map(OptionSnapshotEntity::getVolume)
                .filter(value -> value != null && value > 0)
                .toList();
        if (volumes.size() < 4) {
            return false;
        }
        double average = volumes.stream().mapToLong(Long::longValue).average().orElse(0);
        long max = volumes.stream().mapToLong(Long::longValue).max().orElse(0);
        return average > 0 && max / average >= tradingProperties.volumeSpikeMultiplier();
    }
}
