package org.tb.stash.user.session.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.tb.stash.user.config.SessionProperties;
import org.tb.stash.user.core.crud.SessionRepository;
import org.tb.stash.user.core.crud.UserRepository;
import org.tb.stash.user.session.domain.AuthenticatedPrincipal;
import org.tb.stash.user.session.exception.SessionExpiredException;

/**
 * Translates the {@code STASH_SESSION} cookie into an {@link AuthenticatedPrincipal} on the request
 * attributes.
 *
 * <p>Does not reject unauthenticated requests — public endpoints ({@code /auth/session}, actuator
 * health) must still pass through. Each authenticated controller is responsible for throwing {@link
 * SessionExpiredException} if the principal is absent.
 *
 * <p>Reads only; never writes {@code last_seen_at} (fixed-expiry sessions, parent spec Q14).
 */
@Component
public class SessionCookieFilter extends OncePerRequestFilter {

    private final SessionProperties sessionProps;
    private final SessionRepository sessions;
    private final UserRepository users;

    public SessionCookieFilter(
            SessionProperties sessionProps, SessionRepository sessions, UserRepository users) {
        this.sessionProps = sessionProps;
        this.sessions = sessions;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String handle = readSessionCookie(request);
        if (handle != null) {
            sessions.findActive(handle, Instant.now())
                    .flatMap(s -> users.findById(s.userId()))
                    .map(AuthenticatedPrincipal::from)
                    .ifPresent(
                            principal ->
                                    request.setAttribute(
                                            AuthenticatedPrincipal.ATTRIBUTE_NAME, principal));
        }
        chain.doFilter(request, response);
    }

    private String readSessionCookie(HttpServletRequest request) {
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
