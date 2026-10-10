package org.tb.stash.user.hiemdall.exception;

/**
 * Hiemdall rejected the handoff code (expired, already redeemed, or wrong app).
 *
 * <p>Mapped to HTTP 401 by {@code login.AuthErrorAdvice}.
 */
@SuppressWarnings("serial")
public class HandoffExpiredException extends RuntimeException {

    public HandoffExpiredException(String message) {
        super(message);
    }
}
