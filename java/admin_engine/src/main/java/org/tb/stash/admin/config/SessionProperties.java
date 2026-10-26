package org.tb.stash.admin.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Admin session-cookie policy. Mirrors user_engine's {@code SessionProperties} shape while sharing
 * no code or state — the admin cookie ({@code STASH_ADMIN_SESSION}) is on a different origin.
 *
 * <p>Fixed-expiry sessions: {@code expiresAt = createdAt + ttl}; never slid on reads.
 *
 * @param cookieName name of the session cookie (default {@code STASH_ADMIN_SESSION}).
 * @param ttl absolute session lifetime from issue; default {@code PT8H}.
 * @param sameSite {@code Lax} / {@code Strict} / {@code None}.
 * @param secure emit {@code Secure} attribute; {@code false} in v1 (plain HTTP on loopback — see
 *     {@code backend/api.md §7.5}).
 * @param httpOnly emit {@code HttpOnly} attribute (always {@code true}).
 */
@Validated
@ConfigurationProperties(prefix = "session")
public record SessionProperties(
        @NotBlank String cookieName,
        @NotNull Duration ttl,
        @NotBlank String sameSite,
        boolean secure,
        boolean httpOnly) {}
