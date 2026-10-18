package org.tb.stash.user.tokenrefresh.domain;

import java.util.Locale;

/**
 * Per-account result of one refresh attempt; drives the sweep summary and the {@code
 * stash.token_refresh} metric's {@code outcome} tag.
 */
public enum AccountRefreshStatus {
    /** New access token stored. */
    REFRESHED,
    /** Nothing to do: token already fresh, a newer token was stored concurrently, or flagged. */
    SKIPPED_FRESH,
    /** Another refresh of the same account holds the lock. */
    SKIPPED_BUSY,
    /** Refresh token dead: account flagged {@code login_required} and all sessions deleted. */
    LOGGED_OUT,
    /** Upstream, network, invalid-body or decrypt failure; retried on the next sweep. */
    TRANSIENT_FAILURE,
    /** Hiemdall rejected our request (secret / app id); every account would fail the same way. */
    CONFIG_ERROR;

    /** Lower-case form used as a metric tag value. */
    public String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
