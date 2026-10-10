package org.tb.stash.user.session.domain;

import java.util.UUID;
import org.tb.stash.user.core.domain.User;

/**
 * Request-scoped principal established by {@link SessionCookieFilter}.
 *
 * <p>Stashed under {@link #ATTRIBUTE_NAME} on the {@code HttpServletRequest}; downstream
 * controllers resolve it from there (no Spring Security on the classpath in Phase 2).
 */
public record AuthenticatedPrincipal(
        UUID userId, String email, String displayName, String picture) {

    public static final String ATTRIBUTE_NAME = "stash.principal";

    public static AuthenticatedPrincipal from(User user) {
        return new AuthenticatedPrincipal(
                user.userId(), user.primaryEmail(), user.displayName(), user.pictureUrl());
    }
}
