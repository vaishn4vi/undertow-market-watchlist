package com.undertow.reconciliation.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "checkins")
public class Checkin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId;

    @Column(name = "checkin_at", nullable = false)
    private Instant checkinAt;

    @Column(name = "previous_checkin_at")
    private Instant previousCheckinAt;

    @Column(name = "days_away", precision = 6, scale = 2)
    private BigDecimal daysAway;

    protected Checkin() {
        // JPA
    }

    public Checkin(UUID userId, String requestId, Instant previousCheckinAt, BigDecimal daysAway) {
        this.userId = userId;
        this.requestId = requestId;
        this.checkinAt = Instant.now();
        this.previousCheckinAt = previousCheckinAt;
        this.daysAway = daysAway;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getCheckinAt() {
        return checkinAt;
    }

    public Instant getPreviousCheckinAt() {
        return previousCheckinAt;
    }

    public BigDecimal getDaysAway() {
        return daysAway;
    }
}
