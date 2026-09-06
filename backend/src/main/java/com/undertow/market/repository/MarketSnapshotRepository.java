package com.undertow.market.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.undertow.market.entity.MarketSnapshot;

public interface MarketSnapshotRepository extends JpaRepository<MarketSnapshot, UUID> {

    Optional<MarketSnapshot> findBySymbolAndAsOf(String symbol, Instant asOf);

    Optional<MarketSnapshot> findTopBySymbolOrderByAsOfDesc(String symbol);

    List<MarketSnapshot> findBySymbolAndAsOfBetweenOrderByAsOfAsc(String symbol, Instant from, Instant to);

    List<MarketSnapshot> findBySymbolOrderByAsOfAsc(String symbol);
}
