package org.tb.stash.user.hiemdall;

/**
 * Hiemdall was reachable but returned a 5xx, or the call failed at the network layer. Mapped to
 * HTTP 502 by {@code login.AuthErrorAdvice}.
 */
@SuppressWarnings("serial")
public class HiemdallUnavailableException extends RuntimeException {

    public HiemdallUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
