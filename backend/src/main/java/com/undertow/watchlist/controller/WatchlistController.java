package com.undertow.watchlist.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.undertow.config.CurrentUser;
import com.undertow.watchlist.dto.AddWatchlistItemRequest;
import com.undertow.watchlist.dto.CreateWatchlistRequest;
import com.undertow.watchlist.dto.UpdateWatchlistItemRequest;
import com.undertow.watchlist.dto.UpdateWatchlistRequest;
import com.undertow.watchlist.dto.WatchlistItemResponse;
import com.undertow.watchlist.dto.WatchlistResponse;
import com.undertow.watchlist.entity.Watchlist;
import com.undertow.watchlist.repository.WatchlistItemRepository;
import com.undertow.watchlist.service.WatchlistService;

@RestController
@RequestMapping("/api/v1/watchlists")
public class WatchlistController {

    private final WatchlistService watchlistService;
    private final WatchlistItemRepository itemRepository; // read-only, for item counts

    public WatchlistController(WatchlistService watchlistService, WatchlistItemRepository itemRepository) {
        this.watchlistService = watchlistService;
        this.itemRepository = itemRepository;
    }

    @GetMapping
    public List<WatchlistResponse> list(@CurrentUser String userId) {
        return watchlistService.listWatchlists(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    public ResponseEntity<WatchlistResponse> create(
            @CurrentUser String userId, @Valid @RequestBody CreateWatchlistRequest request) {
        Watchlist created = watchlistService.createWatchlist(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @PatchMapping("/{watchlistId}")
    public WatchlistResponse update(
            @CurrentUser String userId,
            @PathVariable UUID watchlistId,
            @Valid @RequestBody UpdateWatchlistRequest request) {
        return toResponse(watchlistService.updateWatchlist(userId, watchlistId, request));
    }

    @DeleteMapping("/{watchlistId}")
    public ResponseEntity<Void> delete(@CurrentUser String userId, @PathVariable UUID watchlistId) {
        watchlistService.deleteWatchlist(userId, watchlistId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{watchlistId}/items")
    public List<WatchlistItemResponse> listItems(@CurrentUser String userId, @PathVariable UUID watchlistId) {
        return watchlistService.listItems(userId, watchlistId).stream()
                .map(WatchlistItemResponse::from)
                .toList();
    }

    @PostMapping("/{watchlistId}/items")
    public ResponseEntity<WatchlistItemResponse> addItem(
            @CurrentUser String userId,
            @PathVariable UUID watchlistId,
            @Valid @RequestBody AddWatchlistItemRequest request) {
        var item = watchlistService.addItem(userId, watchlistId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(WatchlistItemResponse.from(item));
    }

    @DeleteMapping("/{watchlistId}/items/{itemId}")
    public ResponseEntity<Void> removeItem(
            @CurrentUser String userId, @PathVariable UUID watchlistId, @PathVariable UUID itemId) {
        watchlistService.removeItem(userId, watchlistId, itemId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{watchlistId}/items/{itemId}")
    public WatchlistItemResponse updateItem(
            @CurrentUser String userId,
            @PathVariable UUID watchlistId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateWatchlistItemRequest request) {
        return WatchlistItemResponse.from(
                watchlistService.updateItemPosition(userId, watchlistId, itemId, request));
    }

    private WatchlistResponse toResponse(Watchlist watchlist) {
        int itemCount = itemRepository.countByWatchlistId(watchlist.getId());
        return WatchlistResponse.from(watchlist, itemCount);
    }
}
