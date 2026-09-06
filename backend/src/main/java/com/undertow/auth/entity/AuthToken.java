package com.undertow.auth.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A single opaque bearer token identifying a logged-in session. Stored as
 * the raw random value rather than a hash of it - a deliberate scope
 * decision (see AuthService for the entropy used to generate it), not an
 * oversight: hashing session tokens the way passwords are hashed is a
 * reasonable production hardening step, but isn't necessary for a token
 * that's already high-entropy and never derived from user input.
 */
@Entity
@Table(name = "auth_tokens")
public class AuthToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token", nullable = false, unique = true, length = 128)
    private String token;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected AuthToken() {
        // JPA
    }

    public AuthToken(UUID userId, String token, Instant createdAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.token = token;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
