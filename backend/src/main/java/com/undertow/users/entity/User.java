package com.undertow.users.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_checkin_at")
    private Instant lastCheckinAt;

    // Null for any pre-existing row created before real authentication was
    // added (see V4__auth_credentials.sql) - those accounts simply can't
    // log in anymore. Always set for accounts created via /api/v1/auth/signup.
    @Column(name = "password_hash")
    private String passwordHash;

    protected User() {
        // JPA
    }

    public User(String externalId, String displayName) {
        this.externalId = externalId;
        this.displayName = displayName;
        this.createdAt = Instant.now();
    }

    public User(String externalId, String displayName, String passwordHash) {
        this(externalId, displayName);
        this.passwordHash = passwordHash;
    }

    public UUID getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastCheckinAt() {
        return lastCheckinAt;
    }

    public void setLastCheckinAt(Instant lastCheckinAt) {
        this.lastCheckinAt = lastCheckinAt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
