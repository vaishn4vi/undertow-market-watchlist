package com.undertow.attention.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.undertow.attention.entity.AttentionDebtSnapshot;

public interface AttentionDebtSnapshotRepository extends JpaRepository<AttentionDebtSnapshot, UUID> {
    Optional<AttentionDebtSnapshot> findFirstByUserIdOrderByComputedAtDesc(UUID userId);

    List<AttentionDebtSnapshot> findByUserIdOrderByComputedAtAsc(UUID userId);
}
