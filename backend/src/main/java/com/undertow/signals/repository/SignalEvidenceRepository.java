package com.undertow.signals.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.undertow.signals.entity.SignalEvidence;

public interface SignalEvidenceRepository extends JpaRepository<SignalEvidence, UUID> {
    Optional<SignalEvidence> findBySignalEventId(UUID signalEventId);
}
