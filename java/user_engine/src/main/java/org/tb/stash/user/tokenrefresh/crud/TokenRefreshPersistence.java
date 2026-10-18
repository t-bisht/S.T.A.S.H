package org.tb.stash.user.tokenrefresh.crud;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.tb.stash.user.core.crud.SessionRepository;
import org.tb.stash.user.core.crud.UserOAuthAccountRepository;
import org.tb.stash.user.crypto.Ciphertext;
import org.tb.stash.user.tokenrefresh.domain.RefreshFailureCode;

/**
 * Transactional writes of the token-refresh feature.
 *
 * <p>Its own bean so the service calls it through the Spring proxy (self-invocation would bypass
 * {@code @Transactional}). Each method is one short transaction; the Hiemdall call always happens
 * before, outside any transaction.
 */
@Component
public class TokenRefreshPersistence {

    private static final Logger log = LoggerFactory.getLogger(TokenRefreshPersistence.class);

    private final UserOAuthAccountRepository accounts;
    private final SessionRepository sessions;

    public TokenRefreshPersistence(
            UserOAuthAccountRepository accounts, SessionRepository sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    /**
     * Stores a freshly minted access token (and the rotated refresh token, if any).
     *
     * <p>Conditional write: nothing is stored when the row already holds a token expiring at or
     * after {@code expiresAt} (e.g. a concurrent login won) or the account is flagged {@code
     * login_required}.
     *
     * @param accountId account to update
     * @param access encrypted new access token
     * @param expiresAt wall-clock expiry of the new access token
     * @param scopeOrNull granted scopes; {@code null} keeps the stored value
     * @param refreshOrNull encrypted rotated refresh token; {@code null} keeps the stored one
     * @param now refresh time, stored as {@code last_refreshed_at}
     * @return {@code true} if the row was updated
     */
    @Transactional
    public boolean storeRefreshed(
            UUID accountId,
            Ciphertext access,
            Instant expiresAt,
            String scopeOrNull,
            Ciphertext refreshOrNull,
            Instant now) {
        int updated =
                refreshOrNull == null
                        ? accounts.updateAccessToken(
                                accountId,
                                access.ciphertext(),
                                access.nonce(),
                                expiresAt,
                                scopeOrNull,
                                now)
                        : accounts.updateAccessAndRefreshToken(
                                accountId,
                                access.ciphertext(),
                                access.nonce(),
                                expiresAt,
                                refreshOrNull.ciphertext(),
                                refreshOrNull.nonce(),
                                scopeOrNull,
                                now);
        return updated > 0;
    }

    /**
     * Forced logout after a dead refresh token: flags the account {@code login_required} and
     * deletes every session of the user (all devices) in one transaction.
     *
     * @param accountId account whose refresh token is dead
     * @param userId owner of the account
     * @return number of sessions deleted
     */
    @Transactional
    public int forceLogout(UUID accountId, UUID userId) {
        accounts.markLoginRequired(accountId);
        int sessionsDeleted = sessions.deleteByUserId(userId);
        log.info(
                "token_refresh.forced_logout userId={} sessionsDeleted={}",
                userId,
                sessionsDeleted);
        return sessionsDeleted;
    }

    /**
     * Records why the last refresh attempt failed ({@code last_refresh_error}).
     *
     * @param accountId account that failed to refresh
     * @param code stable failure code
     */
    @Transactional
    public void recordFailure(UUID accountId, RefreshFailureCode code) {
        accounts.recordRefreshError(accountId, code.code());
    }
}
