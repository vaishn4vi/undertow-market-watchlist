package com.undertow.watchlist.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.undertow.common.exception.InvalidRequestException;
import com.undertow.common.exception.NotFoundException;
import com.undertow.market.service.Symbol;
import com.undertow.market.service.SymbolDirectory;
import com.undertow.users.entity.User;
import com.undertow.users.service.UserService;
import com.undertow.watchlist.dto.AddWatchlistItemRequest;
import com.undertow.watchlist.dto.CreateWatchlistRequest;
import com.undertow.watchlist.dto.UpdateWatchlistItemRequest;
import com.undertow.watchlist.dto.UpdateWatchlistRequest;
import com.undertow.watchlist.entity.Watchlist;
import com.undertow.watchlist.entity.WatchlistItem;
import com.undertow.watchlist.repository.WatchlistItemRepository;
import com.undertow.watchlist.repository.WatchlistRepository;

@Service
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository itemRepository;
    private final UserService userService;
    private final SymbolDirectory symbolDirectory;

    public WatchlistService(
            WatchlistRepository watchlistRepository,
            WatchlistItemRepository itemRepository,
            UserService userService,
            SymbolDirectory symbolDirectory
    ) {
        this.watchlistRepository = watchlistRepository;
        this.itemRepository = itemRepository;
        this.userService = userService;
        this.symbolDirectory = symbolDirectory;
    }

    @Transactional
    public List<Watchlist> listWatchlists(String externalUserId) {
        User user = userService.getOrCreate(externalUserId);
        return watchlistRepository.findByUserIdOrderByPositionAsc(user.getId());
    }

    @Transactional
    public Watchlist createWatchlist(String externalUserId, CreateWatchlistRequest request) {
        User user = userService.getOrCreate(externalUserId);
        int nextPosition = watchlistRepository.countByUserId(user.getId());
        Watchlist watchlist = new Watchlist(user.getId(), request.name().trim(), nextPosition);
        return watchlistRepository.save(watchlist);
    }

    @Transactional
    public Watchlist updateWatchlist(String externalUserId, UUID watchlistId, UpdateWatchlistRequest request) {
        Watchlist watchlist = requireOwnedWatchlist(externalUserId, watchlistId);
        if (request.name() != null && !request.name().isBlank()) {
            watchlist.setName(request.name().trim());
        }
        if (request.position() != null) {
            watchlist.setPosition(request.position());
        }
        return watchlistRepository.save(watchlist);
    }

    @Transactional
    public void deleteWatchlist(String externalUserId, UUID watchlistId) {
        Watchlist watchlist = requireOwnedWatchlist(externalUserId, watchlistId);
        // Deleted explicitly at the service layer rather than relying solely on
        // the DB's ON DELETE CASCADE - keeps behavior identical across Postgres
        // and the H2 test database, and makes the cascade visible in one place.
        itemRepository.deleteAll(itemRepository.findByWatchlistIdOrderByPositionAsc(watchlist.getId()));
        watchlistRepository.delete(watchlist);
    }

    @Transactional
    public List<WatchlistItem> listItems(String externalUserId, UUID watchlistId) {
        Watchlist watchlist = requireOwnedWatchlist(externalUserId, watchlistId);
        return itemRepository.findByWatchlistIdOrderByPositionAsc(watchlist.getId());
    }

    @Transactional
    public WatchlistItem addItem(String externalUserId, UUID watchlistId, AddWatchlistItemRequest request) {
        Watchlist watchlist = requireOwnedWatchlist(externalUserId, watchlistId);

        String ticker = request.symbol().trim().toUpperCase();
        Symbol symbol = symbolDirectory.find(ticker)
                .orElseThrow(() -> new InvalidRequestException("Unknown symbol: " + ticker));

        // Adding the same symbol twice is a no-op, not an error - the user
        // almost certainly meant "make sure it's there", not "add a duplicate".
        return itemRepository.findByWatchlistIdAndSymbol(watchlist.getId(), symbol.ticker())
                .orElseGet(() -> insertItemSafely(watchlist, symbol));
    }

    private WatchlistItem insertItemSafely(Watchlist watchlist, Symbol symbol) {
        int nextPosition = itemRepository.countByWatchlistId(watchlist.getId());
        WatchlistItem item = new WatchlistItem(watchlist.getId(), symbol.ticker(), symbol.sector(), nextPosition);
        try {
            return itemRepository.save(item);
        } catch (DataIntegrityViolationException concurrentDuplicateAdd) {
            return itemRepository.findByWatchlistIdAndSymbol(watchlist.getId(), symbol.ticker())
                    .orElseThrow(() -> concurrentDuplicateAdd);
        }
    }

    @Transactional
    public void removeItem(String externalUserId, UUID watchlistId, UUID itemId) {
        Watchlist watchlist = requireOwnedWatchlist(externalUserId, watchlistId);
        WatchlistItem item = itemRepository.findByIdAndWatchlistId(itemId, watchlist.getId())
                .orElseThrow(() -> NotFoundException.of("WatchlistItem", itemId));
        itemRepository.delete(item);
    }

    @Transactional
    public WatchlistItem updateItemPosition(
            String externalUserId, UUID watchlistId, UUID itemId, UpdateWatchlistItemRequest request) {
        Watchlist watchlist = requireOwnedWatchlist(externalUserId, watchlistId);
        WatchlistItem item = itemRepository.findByIdAndWatchlistId(itemId, watchlist.getId())
                .orElseThrow(() -> NotFoundException.of("WatchlistItem", itemId));
        item.setPosition(request.position());
        return itemRepository.save(item);
    }

    /**
     * Looks up a watchlist and verifies the current user owns it in one step.
     * Returns 404 rather than 403 for a watchlist owned by someone else -
     * deliberately not confirming that the id exists at all to a non-owner.
     */
    private Watchlist requireOwnedWatchlist(String externalUserId, UUID watchlistId) {
        User user = userService.getOrCreate(externalUserId);
        return watchlistRepository.findByIdAndUserId(watchlistId, user.getId())
                .orElseThrow(() -> NotFoundException.of("Watchlist", watchlistId));
    }
}
