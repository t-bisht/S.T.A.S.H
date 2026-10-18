package org.tb.stash.user.hiemdall.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.tb.stash.user.crypto.TokenMask;

/**
 * 200 body of Hiemdall's {@code POST /internal/auth/google/refresh}; mirrors Hiemdall's {@code
 * OAuthTokenResponse} (RFC 6749 §5.1).
 *
 * <p>Google normally omits {@code refresh_token} on a refresh; a non-null value means the refresh
 * token was rotated and must replace the stored one. {@code id_token} is ignored.
 *
 * <p>{@link #toString()} masks both tokens so the record is safe to log.
 *
 * @param accessToken new short-lived bearer token
 * @param refreshToken rotated refresh token, or {@code null} when not rotated
 * @param expiresIn access-token lifetime in seconds; {@code null} if Hiemdall omitted it
 * @param scope space-separated granted scopes; may be {@code null}
 * @param tokenType typically {@code "Bearer"}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HiemdallRefreshResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("scope") String scope,
        @JsonProperty("token_type") String tokenType) {

    @Override
    public String toString() {
        return "HiemdallRefreshResponse{accessToken="
                + TokenMask.mask(accessToken)
                + ", refreshToken="
                + TokenMask.mask(refreshToken)
                + ", expiresIn="
                + expiresIn
                + ", scope="
                + scope
                + ", tokenType="
                + tokenType
                + '}';
    }
}
