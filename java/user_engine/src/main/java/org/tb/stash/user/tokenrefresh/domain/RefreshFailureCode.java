package org.tb.stash.user.tokenrefresh.domain;

/**
 * Stable codes written to {@code user_oauth_accounts.last_refresh_error}.
 *
 * <p>{@code reauth_required} is written by the forced-logout query itself and is not listed here.
 */
public enum RefreshFailureCode {
    /** Hiemdall 5xx or unreachable. */
    UPSTREAM_UNAVAILABLE("upstream_unavailable"),
    /** Stored ciphertext could not be decrypted (key change or tampering). */
    DECRYPT_FAILED("decrypt_failed"),
    /** Hiemdall returned 200 with an unusable body. */
    INVALID_UPSTREAM_RESPONSE("invalid_upstream_response"),
    /** Hiemdall rejected our request (secret / app id). */
    CONFIG_ERROR("config_error");

    private final String code;

    RefreshFailureCode(String code) {
        this.code = code;
    }

    /** Column value. */
    public String code() {
        return code;
    }
}
