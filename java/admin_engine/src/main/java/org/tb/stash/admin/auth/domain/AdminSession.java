package org.tb.stash.admin.auth.domain;

import java.time.Instant;

/**
 * An active admin session held in the in-process store.
 *
 * @param token opaque 256-bit session token (the cookie value).
 * @param username the authenticated admin actor.
 * @param createdAt when the session was minted.
 * @param expiresAt absolute expiry ({@code createdAt + ttl}); never slid on reads.
 */
public record AdminSession(String token, String username, Instant createdAt, Instant expiresAt) {}
