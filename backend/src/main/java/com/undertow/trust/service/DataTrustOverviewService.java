package com.undertow.trust.service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.undertow.trust.dto.DataTrustOverviewResponse;
import com.undertow.trust.model.TrustStatus;
import com.undertow.users.service.UserService;
import com.undertow.watchlist.entity.Watchlist;
import com.undertow.watchlist.entity.WatchlistItem;
import com.undertow.watchlist.repository.WatchlistItemRepository;
import com.undertow.watchlist.repository.WatchlistRepository;

/**
 * Aggregates the existing per-symbol TrustService.assess() results across
 * every symbol the user currently tracks (union of items across all of
 * their watchlists), for the global "is my market data trustworthy right
 * now" indicator. This adds no new trust logic - it is strictly a
 * fan-out/reduce over TrustService, one call per distinct tracked symbol.
 */
@Service
public class DataTrustOverviewService {

    // Worst-first ordering used to pick a single headline status: if any
    // tracked symbol has no usable data at all, the whole indicator should
    // say so rather than being diluted by symbols that are fine.
    private static final List<TrustStatus> SEVERITY_WORST_FIRST = List.of(
            TrustStatus.UNAVAILABLE,
            TrustStatus.CONFLICTING,
            TrustStatus.STALE,
            TrustStatus.DELAYED,
            TrustStatus.LIVE
    );

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository itemRepository;
    private final TrustService trustService;
    private final UserService userService;

    public DataTrustOverviewService(
            WatchlistRepository watchlistRepository,
            WatchlistItemRepository itemRepository,
            TrustService trustService,
            UserService userService
    ) {
        this.watchlistRepository = watchlistRepository;
        this.itemRepository = itemRepository;
        this.trustService = trustService;
        this.userService = userService;
    }

    public DataTrustOverviewResponse overviewForUser(String externalUserId) {
        UUID internalUserId = userService.getOrCreate(externalUserId).getId();

        List<UUID> watchlistIds = watchlistRepository.findByUserIdOrderByPositionAsc(internalUserId).stream()
                .map(Watchlist::getId)
                .toList();

        Set<String> symbols = new LinkedHashSet<>();
        if (!watchlistIds.isEmpty()) {
            for (WatchlistItem item : itemRepository.findByWatchlistIdIn(watchlistIds)) {
                symbols.add(item.getSymbol());
            }
        }

        if (symbols.isEmpty()) {
            return new DataTrustOverviewResponse("UNKNOWN", 0, Map.of(), Instant.now());
        }

        Map<TrustStatus, Integer> counts = new EnumMap<>(TrustStatus.class);
        for (String symbol : symbols) {
            TrustStatus status = trustService.assess(symbol)
                    .map(TrustAssessment::status)
                    // Never ingested at all - closest existing status is
                    // UNAVAILABLE (no usable data), so it still counts
                    // toward the worst-case headline rather than vanishing.
                    .orElse(TrustStatus.UNAVAILABLE);
            counts.merge(status, 1, Integer::sum);
        }

        TrustStatus overall = SEVERITY_WORST_FIRST.stream()
                .filter(counts::containsKey)
                .findFirst()
                .orElse(TrustStatus.LIVE);

        Map<String, Integer> distribution = new LinkedHashMap<>();
        for (TrustStatus status : TrustStatus.values()) {
            distribution.put(status.name(), counts.getOrDefault(status, 0));
        }

        return new DataTrustOverviewResponse(overall.name(), symbols.size(), distribution, Instant.now());
    }
}
