package org.tb.stash.user.tokenrefresh.exception;

import java.util.UUID;

/** The user has no linked Google account, so there is no token to refresh. */
@SuppressWarnings("serial")
public class OAuthAccountNotFoundException extends RuntimeException {

    private final UUID userId;

    public OAuthAccountNotFoundException(UUID userId) {
        super("no google account linked for userId=" + userId);
        this.userId = userId;
    }

    /** User without a linked Google account. */
    public UUID userId() {
        return userId;
    }
}
