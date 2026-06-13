package com.example.demo.repository;

import com.example.demo.domain.InstrumentType;
import com.example.demo.domain.OptionSnapshotEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptionSnapshotRepository extends JpaRepository<OptionSnapshotEntity, Long> {
    List<OptionSnapshotEntity> findByInstrumentAndSnapshotTimeAfterOrderByStrikeAscOptionTypeAsc(
            InstrumentType instrument,
            Instant snapshotTime
    );

    List<OptionSnapshotEntity> findTop100ByInstrumentOrderBySnapshotTimeDesc(InstrumentType instrument);
}
