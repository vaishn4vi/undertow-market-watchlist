package com.undertow.common.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately unauthenticated - a basic liveness endpoint (used by
 * Docker/Render health checks and the earlier manual "is the backend up"
 * curl checks) should work without requiring a login.
 */
@RestController
@RequestMapping("/api/v1/status")
public class StatusController {

    @GetMapping
    public Map<String, Object> status() {
        return Map.of(
                "service", "undertow-backend",
                "status", "ok"
        );
    }
}
