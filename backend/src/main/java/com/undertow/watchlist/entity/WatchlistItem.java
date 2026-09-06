package com.undertow.watchlist.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "watchlist_items")
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "watchlist_id", nullable = false)
    private UUID watchlistId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String sector;

    @Column(nullable = false)
    private int position;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected WatchlistItem() {
        // JPA
    }

    public WatchlistItem(UUID watchlistId, String symbol, String sector, int position) {
        this.watchlistId = watchlistId;
        this.symbol = symbol;
        this.sector = sector;
        this.position = position;
        this.addedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getWatchlistId() {
        return watchlistId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSector() {
        return sector;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
