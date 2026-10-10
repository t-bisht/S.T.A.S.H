package org.tb.stash.user.session.exception;

/**
 * No valid session cookie on an authenticated endpoint (missing, unknown, or expired). Mapped to
 * HTTP 401 by {@code login.AuthErrorAdvice}.
 */
@SuppressWarnings("serial")
public class SessionExpiredException extends RuntimeException {

    public SessionExpiredException(String message) {
        super(message);
    }
}
