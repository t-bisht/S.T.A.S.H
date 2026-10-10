package org.tb.stash.user.session.controller;

import org.springframework.http.ResponseCookie;
import org.tb.stash.user.config.SessionProperties;

/**
 * Builds the {@code Set-Cookie} header value for session mint and clear.
 *
 * <p>Public static helpers so {@code login.LoginController} can produce both the mint-cookie (on
 * {@code POST /auth/session}) and the clear-cookie (on {@code POST /auth/logout}) without
 * duplicating the attribute set.
 */
public final class SessionCookies {

    private SessionCookies() {}

    public static ResponseCookie mint(SessionProperties props, String handle) {
        return ResponseCookie.from(props.cookieName(), handle)
                .httpOnly(props.httpOnly())
                .secure(props.secure())
                .sameSite(props.sameSite())
                .path("/")
                .maxAge(props.ttl())
                .build();
    }

    public static ResponseCookie clear(SessionProperties props) {
        return ResponseCookie.from(props.cookieName(), "")
                .httpOnly(props.httpOnly())
                .secure(props.secure())
                .sameSite(props.sameSite())
                .path("/")
                .maxAge(0)
                .build();
    }
}
