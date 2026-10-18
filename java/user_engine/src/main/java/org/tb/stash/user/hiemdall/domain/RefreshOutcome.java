package org.tb.stash.user.hiemdall.domain;

/**
 * Classified result of one Hiemdall refresh call; produced by {@code HiemdallRefreshClient} and
 * consumed with an exhaustive {@code switch} on {@link #status()}.
 *
 * <ul>
 *   <li>{@link Status#SUCCEEDED} — new access token issued; {@link #body()} is set.
 *   <li>{@link Status#LOGIN_REQUIRED} — refresh token dead; the user must sign in again.
 *   <li>{@link Status#TRANSIENT_FAILURE} — upstream/network trouble; retrying later may succeed.
 *   <li>{@link Status#CONFIG_ERROR} — our request is wrong (secret, app id); every call will fail.
 * </ul>
 *
 * <p>Build instances through the static factories, which keep {@code body}/{@code reason}
 * consistent with the status; the compact constructor rejects any other combination.
 *
 * @param status which case this is
 * @param body validated 200 response; non-null only for {@link Status#SUCCEEDED}
 * @param reason stable failure code; non-null only for the two failure statuses
 */
public record RefreshOutcome(Status status, HiemdallRefreshResponse body, String reason) {

    /** The four mutually exclusive results of a refresh call. */
    public enum Status {
        SUCCEEDED,
        LOGIN_REQUIRED,
        TRANSIENT_FAILURE,
        CONFIG_ERROR
    }

    /** {@code X-Internal-Auth} was missing or did not match Hiemdall's secret. */
    public static final String HIEMDALL_AUTH = "hiemdall_auth";

    /** Hiemdall could not be reached, timed out, or sent an unparseable body. */
    public static final String HIEMDALL_UNREACHABLE = "hiemdall_unreachable";

    /** Hiemdall returned 200 but the body failed validation. */
    public static final String INVALID_UPSTREAM_RESPONSE = "invalid_upstream_response";

    public RefreshOutcome {
        if (status == null) {
            throw new IllegalArgumentException("status");
        }
        boolean succeeded = status == Status.SUCCEEDED;
        boolean failure = status == Status.TRANSIENT_FAILURE || status == Status.CONFIG_ERROR;
        if (succeeded == (body == null)) {
            throw new IllegalArgumentException("body must be set iff status is SUCCEEDED");
        }
        if (failure == (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("reason must be set iff status is a failure");
        }
    }

    /** Hiemdall issued a new access token. */
    public static RefreshOutcome succeeded(HiemdallRefreshResponse body) {
        return new RefreshOutcome(Status.SUCCEEDED, body, null);
    }

    /** Hiemdall answered 401 {@code reauth_required}: the refresh token is expired or revoked. */
    public static RefreshOutcome loginRequired() {
        return new RefreshOutcome(Status.LOGIN_REQUIRED, null, null);
    }

    /** Upstream/network trouble (Hiemdall 5xx, network error, invalid body); retry may succeed. */
    public static RefreshOutcome transientFailure(String reason) {
        return new RefreshOutcome(Status.TRANSIENT_FAILURE, null, reason);
    }

    /** Hiemdall rejected the request itself (400, or 401 from its internal-auth filter). */
    public static RefreshOutcome configError(String reason) {
        return new RefreshOutcome(Status.CONFIG_ERROR, null, reason);
    }
}
