package com.undertow.reconciliation.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.undertow.reconciliation.entity.SignalReconciliation;

public interface SignalReconciliationRepository extends JpaRepository<SignalReconciliation, UUID> {
    List<SignalReconciliation> findByCheckinId(UUID checkinId);
}
