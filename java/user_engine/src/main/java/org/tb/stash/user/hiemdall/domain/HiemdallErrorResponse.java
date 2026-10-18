package org.tb.stash.user.hiemdall.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Error body returned by Hiemdall's internal endpoints: {@code {"error": "<code>",
 * "error_description": "..."}}.
 *
 * <p>Only {@link #error()} is a stable contract; branch on it, never on the description. Hiemdall's
 * {@code InternalAuthFilter} sends {@code error} only (a human-readable reason, not a code).
 *
 * @param error stable error code (e.g. {@code reauth_required}); may be {@code null}
 * @param errorDescription human-readable text; may change at any time
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HiemdallErrorResponse(
        @JsonProperty("error") String error,
        @JsonProperty("error_description") String errorDescription) {

    /** Hiemdall's code for "refresh token expired or revoked — user must sign in again". */
    public static final String REAUTH_REQUIRED = "reauth_required";

    /** Whether this body signals that the user must sign in again. */
    public boolean isReauthRequired() {
        return REAUTH_REQUIRED.equals(error);
    }
}
