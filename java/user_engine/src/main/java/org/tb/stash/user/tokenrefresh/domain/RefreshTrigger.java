package org.tb.stash.user.tokenrefresh.domain;

/** What started a refresh; the {@code trigger} tag of the {@code stash.token_refresh} metric. */
public enum RefreshTrigger {
    /** Scheduled sweep. */
    SWEEP("sweep"),
    /** {@code TokenRefreshService.refreshForUser} — caller saw a Gmail 401. */
    ON_DEMAND("on_demand"),
    /** Access-token fetch by a downstream engine (spec 02). */
    ACCESS_TOKEN_FETCH("access_token_fetch");

    private final String tag;

    RefreshTrigger(String tag) {
        this.tag = tag;
    }

    /** Metric tag value. */
    public String tag() {
        return tag;
    }
}
