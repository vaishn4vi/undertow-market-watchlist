package com.undertow.signals.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.undertow.signals.entity.SignalEvent;

public interface SignalEventRepository extends JpaRepository<SignalEvent, UUID> {

    List<SignalEvent> findBySymbolOrderByDetectedAtDesc(String symbol);

    Optional<SignalEvent> findBySymbolAndTypeAndSnapshotId(String symbol, String type, UUID snapshotId);
}
