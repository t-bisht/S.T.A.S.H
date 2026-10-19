package org.tb.stash.user.tokenrefresh.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BiPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.tb.stash.user.config.TokenRefreshProperties;
import org.tb.stash.user.core.crud.UserOAuthAccountRepository;
import org.tb.stash.user.core.domain.UserOAuthAccount;
import org.tb.stash.user.tokenrefresh.domain.AccountRefreshStatus;
import org.tb.stash.user.tokenrefresh.domain.ExchangeResult;
import org.tb.stash.user.tokenrefresh.domain.IssuedAccessToken;
import org.tb.stash.user.tokenrefresh.domain.RefreshTrigger;
import org.tb.stash.user.tokenrefresh.exception.LoginRequiredException;
import org.tb.stash.user.tokenrefresh.exception.OAuthAccountNotFoundException;
import org.tb.stash.user.tokenrefresh.exception.RefreshUnavailableException;

/**
 * Caller-facing token refresh: hands a valid Google access token to a caller that needs one now.
 *
 * <p>Each call: per-account lock (bounded wait) → re-read → reuse stored token or {@link
 * TokenExchanger#exchange} → map the result to a token or a typed exception → unlock. The scheduled
 * bulk refresh lives in {@link TokenRefreshSweeper}.
 */
@Service
public class TokenRefreshService {

    private static final Logger log = LoggerFactory.getLogger(TokenRefreshService.class);

    private final UserOAuthAccountRepository accounts;
    private final TokenExchanger exchanger;
    private final AccountLocks locks;
    private final TokenRefreshMetrics metrics;
    private final TokenRefreshProperties props;
    private final Clock clock;

    public TokenRefreshService(
            UserOAuthAccountRepository accounts,
            TokenExchanger exchanger,
            AccountLocks locks,
            TokenRefreshMetrics metrics,
            TokenRefreshProperties props,
            Clock clock) {
        this.accounts = accounts;
        this.exchanger = exchanger;
        this.locks = locks;
        this.metrics = metrics;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Mints a new Google access token for {@code userId}, even if the stored one has not expired
     * (the caller saw a Gmail 401, so it may be revoked).
     *
     * <p>Concurrent calls are coalesced: if another refresh of the same account succeeded within
     * {@code token-refresh.coalesce-window} and its token is still valid, that token is returned
     * without calling Hiemdall.
     *
     * @param userId STASH user id
     * @return a valid access token and its expiry
     * @throws OAuthAccountNotFoundException if the user has no linked Google account
     * @throws LoginRequiredException if the refresh token is dead; the user's sessions are gone
     * @throws RefreshUnavailableException if no token can be produced right now (upstream down,
     *     lock timeout, config or decrypt failure); the user stays signed in
     */
    public IssuedAccessToken refreshForUser(UUID userId) {
        UserOAuthAccount account = findGoogleAccount(userId);
        return refreshUnderLock(
                account.oauthAccountId(), userId, RefreshTrigger.ON_DEMAND, this::isCoalescible);
    }

    /**
     * Returns a Google access token for {@code userId} that is valid for at least {@code
     * token-refresh.min-validity}, so an engine can use it without it dying mid-call (spec 02).
     *
     * <p>Fast path: if the stored token still has more than {@code min-validity} left, its
     * plaintext is returned with no lock, no DB write and no Hiemdall call. Otherwise the shared
     * refresh block runs inline (lock → re-read → coalesce or refresh → persist), reusing the
     * stored token only if a concurrent refresh already made it valid again.
     *
     * @param userId STASH user id
     * @return a valid access token and its expiry
     * @throws OAuthAccountNotFoundException if the user has no linked Google account
     * @throws LoginRequiredException if the refresh token is dead; the user's sessions are gone
     * @throws RefreshUnavailableException if no sufficiently valid token can be produced right now
     *     (upstream down, lock timeout, config or decrypt failure); the user stays signed in
     */
    public IssuedAccessToken getAccessToken(UUID userId) {
        UserOAuthAccount account = findGoogleAccount(userId);
        if (hasMinValidity(account, clock.instant())) {
            // G1: read-only fast path — no lock, no write, no Hiemdall call, no metric.
            return exchanger.decryptStored(account);
        }
        IssuedAccessToken token =
                refreshUnderLock(
                        account.oauthAccountId(),
                        userId,
                        RefreshTrigger.ACCESS_TOKEN_FETCH,
                        this::hasMinValidity);
        // G5: never hand out a token that would die within min-validity, even after an inline
        // refresh coalesced onto another writer's token.
        if (!hasMinValidity(token.expiresAt(), clock.instant())) {
            throw new RefreshUnavailableException("insufficient_validity");
        }
        return token;
    }

    private UserOAuthAccount findGoogleAccount(UUID userId) {
        UserOAuthAccount account =
                accounts.findByUserIdAndProvider(userId, UserOAuthAccount.PROVIDER_GOOGLE)
                        .orElseThrow(() -> new OAuthAccountNotFoundException(userId));
        if (account.loginRequired()) {
            log.info("token_refresh.login_required userId={}", userId);
            throw new LoginRequiredException(userId);
        }
        return account;
    }

    /**
     * Shared lock → re-read → reuse-or-refresh → persist block for every caller-facing entry point.
     *
     * @param reuseStored decides, on the freshly re-read row, whether the stored access token can
     *     be returned as-is instead of calling Hiemdall
     */
    private IssuedAccessToken refreshUnderLock(
            UUID accountId,
            UUID userId,
            RefreshTrigger trigger,
            BiPredicate<UserOAuthAccount, Instant> reuseStored) {
        if (!locks.tryLock(accountId, props.lockWait())) {
            metrics.record(trigger, AccountRefreshStatus.SKIPPED_BUSY);
            log.warn("token_refresh.lock_timeout userId={} oauthAccountId={}", userId, accountId);
            throw new RefreshUnavailableException("lock_timeout");
        }
        try {
            UserOAuthAccount current = reread(accountId, userId);
            if (reuseStored.test(current, clock.instant())) {
                metrics.record(trigger, AccountRefreshStatus.SKIPPED_FRESH);
                return exchanger.decryptStored(current);
            }
            ExchangeResult result = exchanger.exchange(current);
            metrics.record(trigger, result.status());
            return toIssuedToken(result, accountId, userId);
        } finally {
            locks.unlock(accountId);
        }
    }

    /** Maps a refresh result to the caller contract of {@link #refreshForUser}. */
    private IssuedAccessToken toIssuedToken(ExchangeResult result, UUID accountId, UUID userId) {
        return switch (result.status()) {
            case REFRESHED -> result.token();
                // Lost a race to a concurrent login that stored a newer token: hand that one out.
            case SKIPPED_FRESH -> exchanger.decryptStored(reread(accountId, userId));
            case LOGGED_OUT -> throw new LoginRequiredException(userId);
            case TRANSIENT_FAILURE, CONFIG_ERROR, SKIPPED_BUSY ->
                    throw new RefreshUnavailableException(result.reason());
        };
    }

    /** Re-reads the row under the lock; another thread may have changed it meanwhile. */
    private UserOAuthAccount reread(UUID accountId, UUID userId) {
        UserOAuthAccount current =
                accounts.findById(accountId)
                        .orElseThrow(() -> new OAuthAccountNotFoundException(userId));
        if (current.loginRequired()) {
            log.info("token_refresh.login_required userId={}", userId);
            throw new LoginRequiredException(userId);
        }
        return current;
    }

    /** The account's stored access token has more than {@code min-validity} life left. */
    private boolean hasMinValidity(UserOAuthAccount account, Instant now) {
        return hasMinValidity(account.accessTokenExpiresAt(), now);
    }

    private boolean hasMinValidity(Instant expiresAt, Instant now) {
        return expiresAt.isAfter(now.plus(props.minValidity()));
    }

    /** A refresh succeeded very recently and its token is still valid. */
    private boolean isCoalescible(UserOAuthAccount account, Instant now) {
        Instant lastRefreshedAt = account.lastRefreshedAt();
        return lastRefreshedAt != null
                && lastRefreshedAt.isAfter(now.minus(props.coalesceWindow()))
                && account.accessTokenExpiresAt().isAfter(now);
    }
}
