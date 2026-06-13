package com.example.demo.repository;

import com.example.demo.domain.AuthSessionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, Long> {
    Optional<AuthSessionEntity> findTopByClientCodeOrderByCreatedAtDesc(String clientCode);
}
