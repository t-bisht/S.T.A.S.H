package org.tb.stash.admin.auth.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Micrometer instruments of the admin auth feature.
 *
 * <ul>
 *   <li>Counter {@code stash.admin.auth_failure} (no tags) — one per failed login attempt.
 * </ul>
 *
 * <p>Carries the {@code stash.} prefix, matching user_engine's {@code stash.token_refresh}. The
 * counter name is a package-private constant so tests assert against it, not a string literal.
 */
@Component
public class AdminAuthMetrics {

    static final String AUTH_FAILURE = "stash.admin.auth_failure";

    private final MeterRegistry registry;

    public AdminAuthMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Counts one failed login attempt (bad username or password). */
    public void recordAuthFailure() {
        registry.counter(AUTH_FAILURE).increment();
    }
}
