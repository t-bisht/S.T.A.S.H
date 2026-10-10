package org.tb.stash.user.me;

import java.util.UUID;

/** {@code GET /me} success body. */
public record MeResponse(UUID userId, String email, String displayName, String picture) {}
