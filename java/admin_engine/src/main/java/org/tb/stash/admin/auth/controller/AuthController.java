package org.tb.stash.admin.auth.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.tb.stash.admin.auth.domain.AdminSession;
import org.tb.stash.admin.auth.domain.LoginRequest;
import org.tb.stash.admin.auth.domain.MeResponse;
import org.tb.stash.admin.auth.service.AdminAuthService;
import org.tb.stash.admin.config.SessionProperties;
import org.tb.stash.admin.core.domain.ErrorBody;

/**
 * Admin auth surface.
 *
 * <ul>
 *   <li>{@code POST /admin/login} — credential check → session cookie (204) or 401.
 *   <li>{@code POST /admin/logout} — clear the cookie (204), regardless of session state.
 *   <li>{@code GET /admin/api/me} — session probe for UI bootstrap (behind the interceptor).
 * </ul>
 *
 * <p>{@code /admin/login} and {@code /admin/logout} are outside the {@code /admin/api/**}
 * interceptor, so the login 401 is written here rather than by {@code SessionInterceptor} — both
 * serialise {@link ErrorBody}.
 */
@RestController
public class AuthController {

    private final AdminAuthService authService;
    private final SessionProperties sessionProps;

    public AuthController(AdminAuthService authService, SessionProperties sessionProps) {
        this.authService = authService;
        this.sessionProps = sessionProps;
    }

    @PostMapping("/admin/login")
    public ResponseEntity<Object> login(@Valid @RequestBody LoginRequest body) {
        Optional<AdminSession> session = authService.login(body.username(), body.password());
        if (session.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorBody("unauthorized", "Invalid credentials"));
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, mintCookie(session.get().token()).toString())
                .build();
    }

    @PostMapping("/admin/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(readCookie(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                .build();
    }

    @GetMapping("/admin/api/me")
    public ResponseEntity<MeResponse> me(HttpServletRequest request) {
        String actor = (String) request.getAttribute(SessionInterceptor.ACTOR_ATTRIBUTE);
        return ResponseEntity.ok(new MeResponse(actor));
    }

    private ResponseCookie mintCookie(String token) {
        return ResponseCookie.from(sessionProps.cookieName(), token)
                .httpOnly(sessionProps.httpOnly())
                .secure(sessionProps.secure())
                .sameSite(sessionProps.sameSite())
                .path("/")
                .maxAge(sessionProps.ttl())
                .build();
    }

    private ResponseCookie clearCookie() {
        return ResponseCookie.from(sessionProps.cookieName(), "")
                .httpOnly(sessionProps.httpOnly())
                .secure(sessionProps.secure())
                .sameSite(sessionProps.sameSite())
                .path("/")
                .maxAge(0)
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
