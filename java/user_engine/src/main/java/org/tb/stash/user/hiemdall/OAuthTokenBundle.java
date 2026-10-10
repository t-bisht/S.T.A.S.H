package org.tb.stash.user.hiemdall;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Google-side token bundle as returned by Hiemdall's {@code /internal/auth/exchange}.
 *
 * <p>STASH persists {@code refresh_token}, {@code access_token}, {@code expires_in} (as a
 * wall-clock {@code access_token_expires_at}), and {@code scope}. {@code id_token} is read then
 * dropped (parent spec D1). {@code idToken} is retained on the DTO so STASH can log at DEBUG if
 * needed.
 */
public record OAuthTokenBundle(
        @JsonProperty("id_token") String idToken,
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") long expiresIn,
        String scope,
        @JsonProperty("token_type") String tokenType) {}
