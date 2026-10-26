package org.tb.stash.admin.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.tb.stash.admin.auth.domain.AdminSession;
import org.tb.stash.admin.auth.service.AdminAuthService;
import org.tb.stash.admin.config.SessionProperties;
import org.tb.stash.admin.core.domain.ErrorBody;

/**
 * Enforces the admin session on {@code /admin/api/**} (registered in {@code AdminWebMvcConfig}).
 * Lives in {@code controller/} — the same placement as user_engine's {@code SessionCookieFilter}
 * and {@code InternalAuthFilter}.
 *
 * <p>Reads the {@code STASH_ADMIN_SESSION} cookie, validates it via {@link AdminAuthService}, and
 * on success attaches the actor as the {@value #ACTOR_ATTRIBUTE} request attribute for controllers
 * to read. A missing or expired session yields a {@code 401 unauthorized} {@link ErrorBody} written
 * directly (this runs before the dispatcher, so {@code @RestControllerAdvice} cannot catch it).
 */
@Component
public class SessionInterceptor implements HandlerInterceptor {

    /** Request attribute holding the authenticated admin username. */
    public static final String ACTOR_ATTRIBUTE = "adminActor";

    private static final ErrorBody UNAUTHORIZED =
            new ErrorBody("unauthorized", "No active admin session.");

    private final AdminAuthService authService;
    private final SessionProperties sessionProps;
    private final ObjectMapper json;

    public SessionInterceptor(
            AdminAuthService authService, SessionProperties sessionProps, ObjectMapper json) {
        this.authService = authService;
        this.sessionProps = sessionProps;
        this.json = json;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        Optional<AdminSession> session = authService.validate(readCookie(request));
        if (session.isEmpty()) {
            writeUnauthorized(response);
            return false;
        }
        request.setAttribute(ACTOR_ATTRIBUTE, session.get().username());
        return true;
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

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getWriter().write(json.writeValueAsString(UNAUTHORIZED));
    }
}
