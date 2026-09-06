package com.undertow.watchlist.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.undertow.watchlist.entity.Watchlist;

public interface WatchlistRepository extends JpaRepository<Watchlist, UUID> {
    List<Watchlist> findByUserIdOrderByPositionAsc(UUID userId);

    Optional<Watchlist> findByIdAndUserId(UUID id, UUID userId);

    int countByUserId(UUID userId);
}
