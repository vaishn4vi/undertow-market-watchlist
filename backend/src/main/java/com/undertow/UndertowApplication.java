package com.undertow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * UNDERTOW backend entrypoint.
 *
 * Modular monolith: modules (watchlist, market, signals, trust, attention,
 * reconciliation, backtest, users) live as top-level packages under
 * com.undertow and talk to each other only through their service interfaces.
 * See /docs/architecture.md for the full data-flow diagram.
 */
@SpringBootApplication
public class UndertowApplication {

    public static void main(String[] args) {
        SpringApplication.run(UndertowApplication.class, args);
    }
}
