package org.tb.stash.user.tokenrefresh.exception;

import java.util.UUID;

/**
 * The user's Google refresh token is dead ({@code login_required}); only a fresh sign-in can mint
 * new access tokens. All of the user's sessions have already been deleted.
 */
@SuppressWarnings("serial")
public class LoginRequiredException extends RuntimeException {

    private final UUID userId;

    public LoginRequiredException(UUID userId) {
        super("login required for userId=" + userId);
        this.userId = userId;
    }

    /** User who must sign in again. */
    public UUID userId() {
        return userId;
    }
}
