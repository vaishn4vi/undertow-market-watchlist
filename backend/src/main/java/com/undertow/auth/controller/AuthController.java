package com.undertow.auth.controller;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.undertow.auth.dto.AuthResponse;
import com.undertow.auth.dto.LoginRequest;
import com.undertow.auth.dto.MeResponse;
import com.undertow.auth.dto.SignupRequest;
import com.undertow.auth.service.AuthService;
import com.undertow.common.exception.UnauthorizedException;
import com.undertow.users.entity.User;
import com.undertow.users.repository.UserRepository;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        User user = authService.signup(request.email(), request.password(), request.displayName());
        String token = authService.issueToken(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(token, user.getExternalId(), user.getDisplayName()));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        User user = authService.login(request.email(), request.password());
        String token = authService.issueToken(user);
        return new AuthResponse(token, user.getExternalId(), user.getDisplayName());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        extractBearerToken(request).ifPresent(authService::logout);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me(HttpServletRequest request) {
        String token = extractBearerToken(request)
                .orElseThrow(() -> new UnauthorizedException("Not logged in."));
        String externalId = authService.resolveExternalIdFromToken(token)
                .orElseThrow(() -> new UnauthorizedException("Session expired. Please log in again."));
        User user = userRepository.findByExternalId(externalId)
                .orElseThrow(() -> new UnauthorizedException("Session expired. Please log in again."));
        return new MeResponse(user.getExternalId(), user.getDisplayName());
    }

    private Optional<String> extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return Optional.empty();
        }
        return Optional.of(header.substring("Bearer ".length()));
    }
}
