package com.undertow.users.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.undertow.users.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByExternalId(String externalId);
}
