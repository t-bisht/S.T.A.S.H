package org.tb.stash.admin.auth.domain;

/**
 * {@code GET /admin/api/me} success body — the authenticated admin actor, for UI bootstrap.
 *
 * @param username the admin actor attached to the session.
 */
public record MeResponse(String username) {}
