package com.undertow.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.undertow.auth.entity.AuthToken;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {
    Optional<AuthToken> findByToken(String token);

    void deleteByToken(String token);

    void deleteByUserId(UUID userId);
}
