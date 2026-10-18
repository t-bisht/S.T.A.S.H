package org.tb.stash.user.tokenrefresh.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.tb.stash.user.core.domain.UserOAuthAccount;
import org.tb.stash.user.crypto.Ciphertext;
import org.tb.stash.user.crypto.TokenCipher;
import org.tb.stash.user.crypto.TokenCipherException;
import org.tb.stash.user.hiemdall.domain.HiemdallRefreshResponse;
import org.tb.stash.user.hiemdall.domain.RefreshOutcome;
import org.tb.stash.user.hiemdall.service.HiemdallRefreshClient;
import org.tb.stash.user.tokenrefresh.crud.TokenRefreshPersistence;
import org.tb.stash.user.tokenrefresh.domain.AccountRefreshStatus;
import org.tb.stash.user.tokenrefresh.domain.ExchangeResult;
import org.tb.stash.user.tokenrefresh.domain.IssuedAccessToken;
import org.tb.stash.user.tokenrefresh.domain.RefreshFailureCode;
import org.tb.stash.user.tokenrefresh.exception.RefreshUnavailableException;

/**
 * One Hiemdall round-trip for one account, plus persisting its outcome.
 *
 * <p>Steps: decrypt refresh token → Hiemdall call (outside any transaction) → apply outcome in a
 * short transaction via {@link TokenRefreshPersistence}. A dead refresh token ({@code
 * reauth_required}) triggers a forced logout: account flagged {@code login_required} and all of the
 * user's sessions deleted.
 *
 * <p>Callers must hold the per-account lock from {@link AccountLocks}. This bean takes no locks and
 * records no {@code stash.token_refresh} counter; both belong to the caller, which knows the
 * trigger.
 *
 * <p>Logs carry ids, outcome codes and HTTP status only — never tokens, nonces or ciphertext.
 */
@Service
public class TokenExchanger {

    private static final Logger log = LoggerFactory.getLogger(TokenExchanger.class);

    private final TokenRefreshPersistence persistence;
    private final HiemdallRefreshClient hiemdall;
    private final TokenCipher cipher;
    private final TokenRefreshMetrics metrics;
    private final Clock clock;

    public TokenExchanger(
            TokenRefreshPersistence persistence,
            HiemdallRefreshClient hiemdall,
            TokenCipher cipher,
            TokenRefreshMetrics metrics,
            Clock clock) {
        this.persistence = persistence;
        this.hiemdall = hiemdall;
        this.cipher = cipher;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Decrypts the refresh token, calls Hiemdall and applies the outcome. Caller holds the lock.
     * Throws only on unexpected (DB / programming) errors.
     */
    public ExchangeResult exchange(UserOAuthAccount account) {
        UUID accountId = account.oauthAccountId();
        String refreshToken;
        try {
            refreshToken = cipher.decrypt(account.refreshTokenEnc(), account.refreshTokenNonce());
        } catch (TokenCipherException e) {
            recordDecryptFailure(accountId);
            return ExchangeResult.failed(
                    AccountRefreshStatus.TRANSIENT_FAILURE,
                    RefreshFailureCode.DECRYPT_FAILED.code());
        }

        Instant requestedAt = clock.instant();
        RefreshOutcome outcome = metrics.timeHiemdall(() -> hiemdall.refresh(refreshToken));
        // handle different response codes
        return switch (outcome.status()) {
            case SUCCEEDED -> store(accountId, outcome.body(), requestedAt);
            case LOGIN_REQUIRED -> forceLogout(account);
            case TRANSIENT_FAILURE -> transientFailure(accountId, outcome.reason());
            case CONFIG_ERROR -> configError(accountId, outcome.reason());
        };
    }

    /**
     * Decrypts the access token already stored on {@code account}.
     *
     * @throws RefreshUnavailableException with {@code decrypt_failed}; the failure is recorded
     */
    public IssuedAccessToken decryptStored(UserOAuthAccount account) {
        try {
            String accessToken =
                    cipher.decrypt(account.accessTokenEnc(), account.accessTokenNonce());
            return new IssuedAccessToken(accessToken, account.accessTokenExpiresAt());
        } catch (TokenCipherException e) {
            recordDecryptFailure(account.oauthAccountId());
            throw new RefreshUnavailableException(RefreshFailureCode.DECRYPT_FAILED.code());
        }
    }

    /**
     * Encrypts and stores the new token. Expiry counts from {@code requestedAt} (before the call),
     * so it can only err on the early side.
     */
    private ExchangeResult store(
            UUID accountId, HiemdallRefreshResponse tokens, Instant requestedAt) {
        Instant expiresAt = requestedAt.plusSeconds(tokens.expiresIn());
        Ciphertext access = cipher.encrypt(tokens.accessToken());
        boolean rotated = tokens.refreshToken() != null;
        Ciphertext refresh = rotated ? cipher.encrypt(tokens.refreshToken()) : null;

        boolean stored =
                persistence.storeRefreshed(
                        accountId, access, expiresAt, tokens.scope(), refresh, clock.instant());
        if (!stored) {
            log.info("token_refresh.superseded oauthAccountId={}", accountId);
            return ExchangeResult.failed(AccountRefreshStatus.SKIPPED_FRESH, "superseded");
        }
        log.info(
                "token_refresh.refreshed oauthAccountId={} expiresAt={} rotated={}",
                accountId,
                expiresAt,
                rotated);
        return ExchangeResult.refreshed(new IssuedAccessToken(tokens.accessToken(), expiresAt));
    }

    private ExchangeResult forceLogout(UserOAuthAccount account) {
        persistence.forceLogout(account.oauthAccountId(), account.userId());
        return ExchangeResult.failed(AccountRefreshStatus.LOGGED_OUT, "reauth_required");
    }

    private ExchangeResult transientFailure(UUID accountId, String reason) {
        RefreshFailureCode code =
                RefreshOutcome.INVALID_UPSTREAM_RESPONSE.equals(reason)
                        ? RefreshFailureCode.INVALID_UPSTREAM_RESPONSE
                        : RefreshFailureCode.UPSTREAM_UNAVAILABLE;
        persistence.recordFailure(accountId, code);
        log.warn("token_refresh.transient_failure oauthAccountId={} reason={}", accountId, reason);
        return ExchangeResult.failed(AccountRefreshStatus.TRANSIENT_FAILURE, code.code());
    }

    private ExchangeResult configError(UUID accountId, String reason) {
        persistence.recordFailure(accountId, RefreshFailureCode.CONFIG_ERROR);
        log.error("token_refresh.config_error oauthAccountId={} reason={}", accountId, reason);
        return ExchangeResult.failed(
                AccountRefreshStatus.CONFIG_ERROR, RefreshFailureCode.CONFIG_ERROR.code());
    }

    private void recordDecryptFailure(UUID accountId) {
        log.error("token_refresh.decrypt_failed oauthAccountId={}", accountId);
        persistence.recordFailure(accountId, RefreshFailureCode.DECRYPT_FAILED);
    }
}
