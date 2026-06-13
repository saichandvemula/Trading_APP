package com.example.demo.repository;

import com.example.demo.domain.InstrumentType;
import com.example.demo.domain.MarketTickEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketTickRepository extends JpaRepository<MarketTickEntity, Long> {
    Optional<MarketTickEntity> findTopBySymbolOrderByTickTimeDesc(String symbol);

    List<MarketTickEntity> findByInstrumentAndTickTimeAfterOrderByTickTimeDesc(InstrumentType instrument, Instant tickTime);
}
