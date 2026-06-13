package com.example.demo.repository;

import com.example.demo.domain.TradingSignalEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradingSignalRepository extends JpaRepository<TradingSignalEntity, Long> {
    Optional<TradingSignalEntity> findTopByStockNameOrderByGeneratedAtDesc(String stockName);
}
