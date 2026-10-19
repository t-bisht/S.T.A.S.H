package org.tb.stash.user.tokenrefresh.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import org.tb.stash.user.crypto.TokenMask;

/**
 * Success body returned to an engine by both {@code /internal/**} token endpoints (spec 02).
 *
 * <p>Field names are the snake_case the engines already parse from Hiemdall. {@link #toString()}
 * masks the token so the DTO is safe to log.
 *
 * @param accessToken plaintext Google bearer token
 * @param tokenType always {@code "Bearer"}
 * @param expiresAt wall-clock expiry of {@code accessToken} (ISO-8601 UTC)
 */
public record AccessTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_at") Instant expiresAt) {

    private static final String BEARER = "Bearer";

    /** Wraps an {@link IssuedAccessToken} as the wire response. */
    public static AccessTokenResponse from(IssuedAccessToken token) {
        return new AccessTokenResponse(token.accessToken(), BEARER, token.expiresAt());
    }

    @Override
    public String toString() {
        return "AccessTokenResponse{accessToken="
                + TokenMask.mask(accessToken)
                + ", tokenType="
                + tokenType
                + ", expiresAt="
                + expiresAt
                + '}';
    }
}
