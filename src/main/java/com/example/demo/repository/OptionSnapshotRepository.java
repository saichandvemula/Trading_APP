package com.example.demo.repository;

import com.example.demo.domain.OptionSnapshotEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptionSnapshotRepository extends JpaRepository<OptionSnapshotEntity, Long> {
    List<OptionSnapshotEntity> findByStockNameAndSnapshotTimeAfterOrderByStrikeAscOptionTypeAsc(
            String stockName,
            Instant snapshotTime
    );

    List<OptionSnapshotEntity> findTop100ByStockNameOrderBySnapshotTimeDesc(String stockName);
}
