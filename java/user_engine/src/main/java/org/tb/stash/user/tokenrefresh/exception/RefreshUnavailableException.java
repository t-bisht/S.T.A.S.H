package org.tb.stash.user.tokenrefresh.exception;

/**
 * A fresh access token could not be produced right now (upstream down, lock timeout, config or
 * decrypt failure). The user stays signed in; the caller may retry later.
 */
@SuppressWarnings("serial")
public class RefreshUnavailableException extends RuntimeException {

    private final String reason;

    public RefreshUnavailableException(String reason) {
        super("token refresh unavailable: " + reason);
        this.reason = reason;
    }

    /** Stable reason code, e.g. {@code lock_timeout}, {@code upstream_unavailable}. */
    public String reason() {
        return reason;
    }
}
