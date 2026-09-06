package com.undertow.market.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.undertow.market.entity.MarketObservation;

public interface MarketObservationRepository extends JpaRepository<MarketObservation, UUID> {
    List<MarketObservation> findBySymbolOrderByReceivedAtDesc(String symbol);

    Optional<MarketObservation> findFirstBySymbolOrderByReceivedAtDesc(String symbol);
}
