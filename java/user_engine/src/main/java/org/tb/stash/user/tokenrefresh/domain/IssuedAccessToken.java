package org.tb.stash.user.tokenrefresh.domain;

import java.time.Instant;
import org.tb.stash.user.crypto.TokenMask;

/**
 * A plaintext Google access token handed to an in-process caller.
 *
 * <p>{@link #toString()} masks the token so the record is safe to log.
 *
 * @param accessToken plaintext bearer token
 * @param expiresAt wall-clock expiry of {@code accessToken}
 */
public record IssuedAccessToken(String accessToken, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedAccessToken{accessToken="
                + TokenMask.mask(accessToken)
                + ", expiresAt="
                + expiresAt
                + '}';
    }
}
