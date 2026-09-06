package com.undertow.auth.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.undertow.auth.entity.AuthToken;
import com.undertow.auth.repository.AuthTokenRepository;
import com.undertow.common.exception.ConflictException;
import com.undertow.common.exception.UnauthorizedException;
import com.undertow.users.entity.User;
import com.undertow.users.repository.UserRepository;

/**
 * Deliberately simple, hackathon-appropriate authentication: email +
 * password, BCrypt hashing, opaque bearer tokens with a fixed expiry. No
 * OAuth, no refresh-token rotation, no email verification - the goal is
 * "one user's data can never leak into another's", not a production auth
 * system. See CurrentUserArgumentResolver for how a token on a request
 * gets turned into the externalId every other service already expects.
 */
@Service
public class AuthService {

    private static final int TOKEN_BYTES = 32; // 256 bits of entropy
    private static final long TOKEN_TTL_DAYS = 30;

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository, AuthTokenRepository authTokenRepository) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
    }

    @Transactional
    public User signup(String email, String rawPassword, String displayName) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.findByExternalId(normalizedEmail).isPresent()) {
            throw new ConflictException("An account with this email already exists.");
        }

        String name = (displayName == null || displayName.isBlank()) ? normalizedEmail : displayName.trim();
        User user = new User(normalizedEmail, name, passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    @Transactional
    public User login(String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByExternalId(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password."));

        // Same message for "no account" and "wrong password" - confirming
        // which one it was would let an attacker enumerate registered
        // emails. passwordHash is null for any pre-auth legacy row; those
        // simply can never match.
        if (user.getPasswordHash() == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password.");
        }

        return user;
    }

    @Transactional
    public String issueToken(User user) {
        String token = generateToken();
        Instant now = Instant.now();
        authTokenRepository.save(new AuthToken(user.getId(), token, now, now.plus(TOKEN_TTL_DAYS, ChronoUnit.DAYS)));
        return token;
    }

    @Transactional
    public void logout(String token) {
        authTokenRepository.deleteByToken(token);
    }

    /**
     * Resolves a bearer token to the externalId (email) every existing
     * service already keys its data on. Returns empty for a missing,
     * unknown, or expired token - the caller (CurrentUserArgumentResolver)
     * is responsible for turning that into a 401.
     */
    @Transactional
    public Optional<String> resolveExternalIdFromToken(String token) {
        return authTokenRepository.findByToken(token)
                .filter(t -> {
                    if (t.isExpired()) {
                        authTokenRepository.deleteByToken(t.getToken());
                        return false;
                    }
                    return true;
                })
                .flatMap(t -> userRepository.findById(t.getUserId()))
                .map(User::getExternalId);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
