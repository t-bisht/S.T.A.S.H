package org.tb.stash.user.login.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tb.stash.user.config.SessionProperties;
import org.tb.stash.user.login.domain.LoginRequest;
import org.tb.stash.user.login.domain.LoginResponse;
import org.tb.stash.user.login.domain.LoginResult;
import org.tb.stash.user.login.service.LoginService;
import org.tb.stash.user.session.controller.SessionCookies;

/**
 * HTTP surface for login and logout.
 *
 * <ul>
 *   <li>{@code POST /auth/session} — redeem a Hiemdall handoff, mint a cookie.
 *   <li>{@code POST /auth/logout} — delete the server-side row + clear cookie.
 * </ul>
 */
@RestController
@RequestMapping("/auth")
public class LoginController {

    private final LoginService service;
    private final SessionProperties sessionProps;

    public LoginController(LoginService service, SessionProperties sessionProps) {
        this.service = service;
        this.sessionProps = sessionProps;
    }

    @PostMapping("/session")
    public ResponseEntity<LoginResponse> createSession(@Valid @RequestBody LoginRequest body) {
        LoginResult result = service.login(body.handoff());
        ResponseCookie cookie = SessionCookies.mint(sessionProps, result.sessionHandle());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.body());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        service.logout(readCookie(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, SessionCookies.clear(sessionProps).toString())
                .build();
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (sessionProps.cookieName().equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
