package org.tb.stash.user.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A single OAuth / OIDC provider account linked to a STASH {@link User}.
 *
 * <p>Mapped to {@code user_oauth_accounts}. Carries provider identity claims ({@code provider_sub},
 * {@code provider_email}), the AES-GCM encrypted long-lived credential ({@code refresh_token_enc} +
 * 12-byte {@code refresh_token_nonce}), and the AES-GCM encrypted short-lived access token ({@code
 * access_token_enc} + {@code access_token_nonce}) alongside its wall-clock {@code
 * access_token_expires_at}. The id token is intentionally not stored.
 *
 * <p>Both token halves are rewritten on every login (first-time and re-login) with fresh ciphertext
 * + fresh nonce; the refresh client that would exercise {@code refresh_token} to mint a new
 * access_token is a deferred feature.
 *
 * <p>Uniqueness is enforced on {@code (provider, provider_sub)}: one row per provider account
 * globally. A single STASH user may own many rows (future multi-provider linking).
 *
 * @param provider identifier string; v1 only {@code "google"}.
 */
@Table("user_oauth_accounts")
public record UserOAuthAccount(
        @Id UUID oauthAccountId,
        UUID userId,
        String provider,
        String providerSub,
        String providerEmail,
        String scope,
        byte[] refreshTokenEnc,
        byte[] refreshTokenNonce,
        byte[] accessTokenEnc,
        byte[] accessTokenNonce,
        Instant accessTokenExpiresAt,
        Instant linkedAt) {

    public static final String PROVIDER_GOOGLE = "google";
}
