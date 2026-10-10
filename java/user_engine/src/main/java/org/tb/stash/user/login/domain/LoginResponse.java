package org.tb.stash.user.login.domain;

import java.util.UUID;

/**
 * {@code POST /auth/session} success body.
 *
 * <p>{@code isNewUser} drives SPA routing ({@code /welcome} vs {@code /dashboard}).
 */
public record LoginResponse(
        UUID userId, String email, String displayName, String picture, boolean isNewUser) {}
