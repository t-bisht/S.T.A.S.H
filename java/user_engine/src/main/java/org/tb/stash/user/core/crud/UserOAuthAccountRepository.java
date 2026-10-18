package org.tb.stash.user.core.crud;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.tb.stash.user.core.domain.UserOAuthAccount;

/**
 * Spring Data JDBC repository for {@link UserOAuthAccount}.
 *
 * <p>Lookup by {@code (provider, provider_sub)} is the hot path during login (first vs returning
 * user decision). The remaining queries back the {@code tokenrefresh} feature: a keyset-paged "due
 * for refresh" scan and conditional token / status updates.
 *
 * <p>The token updates are <em>conditional</em> ({@code access_token_expires_at < :expiresAt} and
 * {@code login_required = false}) so a slower writer can never overwrite a newer token or revive an
 * account that was just force-logged-out. Callers inspect the returned row count.
 */
public interface UserOAuthAccountRepository extends CrudRepository<UserOAuthAccount, UUID> {

    Optional<UserOAuthAccount> findByProviderAndProviderSub(String provider, String providerSub);

    /**
     * Finds the account a user linked for {@code provider}.
     *
     * @param userId STASH user id
     * @param provider provider id, e.g. {@link UserOAuthAccount#PROVIDER_GOOGLE}
     * @return the account, or empty if the user never linked that provider
     */
    Optional<UserOAuthAccount> findByUserIdAndProvider(UUID userId, String provider);

    /**
     * One keyset page of Google accounts whose access token expires before {@code threshold}.
     *
     * <p>Ordered by {@code (access_token_expires_at, oauth_account_id)}; pass the last row of the
     * previous page as {@code (afterExpiry, afterId)} to continue. First page: {@link
     * Instant#EPOCH} and the nil UUID. Accounts flagged {@code login_required} are excluded.
     *
     * @param threshold exclusive upper bound on {@code access_token_expires_at}
     * @param afterExpiry cursor: expiry of the last row already seen
     * @param afterId cursor: id of the last row already seen
     * @param limit page size
     * @return up to {@code limit} due accounts, in cursor order
     */
    @Query(
            """
            SELECT * FROM user_oauth_accounts
             WHERE provider = 'google'
               AND login_required = false
               AND access_token_expires_at < :threshold
               AND (access_token_expires_at, oauth_account_id) > (:afterExpiry, :afterId)
             ORDER BY access_token_expires_at, oauth_account_id
             LIMIT :limit
            """)
    List<UserOAuthAccount> findDueForRefresh(
            @Param("threshold") Instant threshold,
            @Param("afterExpiry") Instant afterExpiry,
            @Param("afterId") UUID afterId,
            @Param("limit") int limit);

    /**
     * Stores a freshly minted access token, keeping the existing refresh token.
     *
     * <p>{@code scope} may be {@code null} to keep the stored value.
     *
     * @return 1 if stored; 0 if a newer token is already stored or the account is flagged {@code
     *     login_required}
     */
    @Modifying
    @Query(
            """
            UPDATE user_oauth_accounts
               SET access_token_enc        = :enc,
                   access_token_nonce      = :nonce,
                   access_token_expires_at = :expiresAt,
                   scope                   = COALESCE(CAST(:scope AS TEXT), scope),
                   last_refreshed_at       = :now,
                   last_refresh_error      = NULL
             WHERE oauth_account_id = :id
               AND login_required = false
               AND access_token_expires_at < :expiresAt
            """)
    int updateAccessToken(
            @Param("id") UUID id,
            @Param("enc") byte[] enc,
            @Param("nonce") byte[] nonce,
            @Param("expiresAt") Instant expiresAt,
            @Param("scope") String scope,
            @Param("now") Instant now);

    /**
     * Same as {@link #updateAccessToken} but also replaces the refresh token. Used only when Google
     * rotated the refresh token in the refresh response.
     *
     * @return 1 if stored; 0 if a newer token is already stored or the account is flagged {@code
     *     login_required}
     */
    @Modifying
    @Query(
            """
            UPDATE user_oauth_accounts
               SET access_token_enc        = :enc,
                   access_token_nonce      = :nonce,
                   access_token_expires_at = :expiresAt,
                   refresh_token_enc       = :refreshEnc,
                   refresh_token_nonce     = :refreshNonce,
                   scope                   = COALESCE(CAST(:scope AS TEXT), scope),
                   last_refreshed_at       = :now,
                   last_refresh_error      = NULL
             WHERE oauth_account_id = :id
               AND login_required = false
               AND access_token_expires_at < :expiresAt
            """)
    int updateAccessAndRefreshToken(
            @Param("id") UUID id,
            @Param("enc") byte[] enc,
            @Param("nonce") byte[] nonce,
            @Param("expiresAt") Instant expiresAt,
            @Param("refreshEnc") byte[] refreshEnc,
            @Param("refreshNonce") byte[] refreshNonce,
            @Param("scope") String scope,
            @Param("now") Instant now);

    /**
     * Flags the account as needing a fresh sign-in (refresh token dead).
     *
     * @return rows updated (0 if the account no longer exists)
     */
    @Modifying
    @Query(
            """
            UPDATE user_oauth_accounts
               SET login_required = true,
                   last_refresh_error = 'reauth_required'
             WHERE oauth_account_id = :id
            """)
    int markLoginRequired(@Param("id") UUID id);

    /**
     * Records the stable code of a failed refresh attempt.
     *
     * @return rows updated (0 if the account no longer exists)
     */
    @Modifying
    @Query(
            "UPDATE user_oauth_accounts SET last_refresh_error = :code"
                    + " WHERE oauth_account_id = :id")
    int recordRefreshError(@Param("id") UUID id, @Param("code") String code);
}
