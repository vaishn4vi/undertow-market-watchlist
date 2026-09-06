package com.undertow.attention.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.undertow.attention.entity.SignalLedgerEntry;

public interface SignalLedgerEntryRepository extends JpaRepository<SignalLedgerEntry, UUID> {

    Optional<SignalLedgerEntry> findFirstByUserIdAndSymbolAndSignalTypeAndStatusNotOrderByFirstDetectedAtDesc(
            UUID userId, String symbol, String signalType, String excludedStatus);

    List<SignalLedgerEntry> findByUserIdAndSymbolOrderByLastDetectedAtDesc(UUID userId, String symbol);

    List<SignalLedgerEntry> findByUserIdOrderByLastDetectedAtDesc(UUID userId);

    Optional<SignalLedgerEntry> findByIdAndUserId(UUID id, UUID userId);
}
