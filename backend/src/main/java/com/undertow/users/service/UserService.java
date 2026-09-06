package com.undertow.users.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.undertow.users.entity.User;
import com.undertow.users.repository.UserRepository;

/**
 * Looks up the User row for an already-authenticated externalId (email).
 * Real accounts are created explicitly via AuthService.signup(); getOrCreate
 * remains as a safe lookup-or-create for any caller that only has a plain
 * externalId string (nothing in the authenticated request path actually
 * hits the "create" branch anymore, since a valid token guarantees the
 * user already exists).
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getOrCreate(String externalId) {
        return userRepository.findByExternalId(externalId)
                .orElseGet(() -> createSafely(externalId));
    }

    private User createSafely(String externalId) {
        try {
            return userRepository.save(new User(externalId, externalId));
        } catch (DataIntegrityViolationException raceLostToConcurrentRequest) {
            // Two requests for the same brand-new demo user arrived concurrently;
            // the loser here just reads what the winner created.
            return userRepository.findByExternalId(externalId)
                    .orElseThrow(() -> raceLostToConcurrentRequest);
        }
    }
}
