package org.tb.stash.user.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Session-cookie policy.
 *
 * <p>Fixed-expiry sessions (parent spec Q14): {@code expiresAt = issuedAt + ttl}; never slid on
 * reads.
 *
 * @param cookieName name of the session cookie (default {@code STASH_SESSION}).
 * @param ttl absolute session lifetime from issue; default {@code P10D}.
 * @param sameSite {@code Lax} / {@code Strict} / {@code None}.
 * @param secure emit {@code Secure} attribute; {@code false} only in local HTTP.
 * @param httpOnly emit {@code HttpOnly} attribute (always {@code true} in prod).
 */
@Validated
@ConfigurationProperties(prefix = "session")
public record SessionProperties(
        @NotBlank String cookieName,
        @NotNull Duration ttl,
        @NotBlank String sameSite,
        boolean secure,
        boolean httpOnly) {}
