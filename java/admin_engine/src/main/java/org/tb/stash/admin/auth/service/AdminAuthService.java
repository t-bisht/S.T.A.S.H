package org.tb.stash.admin.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.tb.stash.admin.auth.domain.AdminSession;
import org.tb.stash.admin.config.AdminCredentialsProperties;
import org.tb.stash.admin.config.SessionProperties;

/**
 * Admin credential check + in-process session store (api.md §7.1–§7.2).
 *
 * <p>Credentials are compared in constant time with {@link MessageDigest#isEqual} (never {@code
 * String.equals}). Session tokens are 256 bits of {@link SecureRandom#nextBytes} material (never
 * {@code generateSeed}, which can block on the OS entropy pool). Sessions live in a {@link
 * ConcurrentHashMap} with an absolute TTL; a process restart clears them — documented v1 behaviour.
 */
@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    private final byte[] expectedUsername;
    private final byte[] expectedPassword;
    private final SessionProperties sessionProps;
    private final AdminAuthMetrics metrics;
    private final Clock clock;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, AdminSession> sessions = new ConcurrentHashMap<>();

    public AdminAuthService(
            AdminCredentialsProperties credentials,
            SessionProperties sessionProps,
            AdminAuthMetrics metrics,
            Clock clock) {
        this.expectedUsername = credentials.username().getBytes(StandardCharsets.UTF_8);
        this.expectedPassword = credentials.password().getBytes(StandardCharsets.UTF_8);
        this.sessionProps = sessionProps;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Validates credentials and, on a match, mints and stores a session.
     *
     * @param username provided username
     * @param password provided password
     * @return the minted {@link AdminSession} on success; empty on mismatch (counter incremented,
     *     {@code login_failed} logged).
     */
    public Optional<AdminSession> login(String username, String password) {
        boolean userOk =
                MessageDigest.isEqual(username.getBytes(StandardCharsets.UTF_8), expectedUsername);
        boolean passOk =
                MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8), expectedPassword);
        if (!(userOk && passOk)) {
            metrics.recordAuthFailure();
            log.warn("action=login_failed reason=bad_credentials");
            return Optional.empty();
        }

        Instant now = clock.instant();
        String token = newToken();
        AdminSession session = new AdminSession(token, username, now, now.plus(sessionProps.ttl()));
        sessions.put(token, session);
        log.info("actor={} action=login", username);
        return Optional.of(session);
    }

    /**
     * Resolves a session token to its live session.
     *
     * @param token cookie value (may be {@code null})
     * @return the live session; empty when absent or expired (an expired entry is removed).
     */
    public Optional<AdminSession> validate(String token) {
        if (token == null) {
            return Optional.empty();
        }
        AdminSession session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (!session.expiresAt().isAfter(clock.instant())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /**
     * Drops a session. Idempotent — a {@code null} or unknown token is a no-op.
     *
     * @param token cookie value to invalidate
     */
    public void logout(String token) {
        if (token != null) {
            AdminSession removed = sessions.remove(token);
            if (removed != null) {
                log.info("actor={} action=logout", removed.username());
            }
        }
    }

    /** 256-bit opaque token, URL-safe Base64 without padding. */
    private String newToken() {
        byte[] buf = new byte[32];
        secureRandom.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
