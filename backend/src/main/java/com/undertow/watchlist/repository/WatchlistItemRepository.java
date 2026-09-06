package com.undertow.watchlist.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.undertow.watchlist.entity.WatchlistItem;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, UUID> {
    List<WatchlistItem> findByWatchlistIdOrderByPositionAsc(UUID watchlistId);

    Optional<WatchlistItem> findByWatchlistIdAndSymbol(UUID watchlistId, String symbol);

    Optional<WatchlistItem> findByIdAndWatchlistId(UUID id, UUID watchlistId);

    int countByWatchlistId(UUID watchlistId);

    List<WatchlistItem> findByWatchlistIdIn(List<UUID> watchlistIds);
}
