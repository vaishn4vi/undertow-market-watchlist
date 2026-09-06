package com.undertow.reconciliation.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.undertow.reconciliation.entity.Checkin;

public interface CheckinRepository extends JpaRepository<Checkin, UUID> {
    Optional<Checkin> findByRequestId(String requestId);

    Optional<Checkin> findFirstByUserIdOrderByCheckinAtDesc(UUID userId);
}
