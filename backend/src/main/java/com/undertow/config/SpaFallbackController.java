package com.undertow.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Once the frontend's built static assets are bundled into this app's
 * classpath (src/main/resources/static), Spring Boot serves them
 * automatically - but only for requests that match a real file. A client-
 * side route like /watchlist or /signals/abc-123 has no matching static
 * file, so without this controller a direct browser navigation or refresh
 * on those URLs would 404 instead of loading the SPA shell and letting
 * React Router take over.
 *
 * Deliberately an explicit list rather than a wildcard/regex catch-all:
 * an @Controller mapping takes priority over Spring's static resource
 * handler, so a broad pattern here would risk swallowing real asset
 * requests (e.g. /assets/index-abc123.js) and breaking the whole site
 * instead of fixing routing. This list must be kept in sync with the
 * top-level routes in frontend/src/App.tsx. "/" itself needs no entry -
 * Spring Boot already serves src/main/resources/static/index.html there
 * by default.
 */
@Controller
public class SpaFallbackController {

    @GetMapping({
            "/watchlist",
            "/watchlist/{watchlistId}",
            "/since-last-checked",
            "/signals/{signalId}",
            "/attention-debt",
            "/replay",
            "/settings",
            "/data-status"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
