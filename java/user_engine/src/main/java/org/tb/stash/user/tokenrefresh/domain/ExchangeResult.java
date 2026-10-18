package org.tb.stash.user.tokenrefresh.domain;

/**
 * Result of one {@code TokenExchanger.exchange} call.
 *
 * @param status per-account status
 * @param token new token; non-null only for {@link AccountRefreshStatus#REFRESHED}
 * @param reason stable failure code otherwise
 */
public record ExchangeResult(AccountRefreshStatus status, IssuedAccessToken token, String reason) {

    public static ExchangeResult refreshed(IssuedAccessToken token) {
        return new ExchangeResult(AccountRefreshStatus.REFRESHED, token, null);
    }

    public static ExchangeResult failed(AccountRefreshStatus status, String reason) {
        return new ExchangeResult(status, null, reason);
    }
}
