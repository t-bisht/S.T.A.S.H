package org.tb.stash.user.tokenrefresh.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.tb.stash.user.config.InternalAuthProperties;
import org.tb.stash.user.tokenrefresh.domain.InternalErrorBody;

/**
 * Guards {@code /internal/**} with the {@code X-Internal-Auth} shared secret (spec 02, §5.1).
 *
 * <p>Only paths under {@code /internal/} are filtered; everything else passes through untouched.
 * The header is compared to {@link InternalAuthProperties#svcToken()} in constant time. A missing,
 * blank or mismatched header yields a 401 {@code unauthorized} JSON body written directly, without
 * invoking the rest of the chain. The header value is never logged.
 */
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalAuthFilter.class);

    private static final String INTERNAL_PREFIX = "/internal/";
    private static final String HEADER = "X-Internal-Auth";
    private static final InternalErrorBody UNAUTHORIZED =
            new InternalErrorBody("unauthorized", "X-Internal-Auth missing or invalid");

    private final byte[] expectedToken;
    private final ObjectMapper json;

    public InternalAuthFilter(InternalAuthProperties props, ObjectMapper json) {
        this.expectedToken = props.svcToken().getBytes(StandardCharsets.UTF_8);
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!tokenMatches(request.getHeader(HEADER))) {
            // Never log the header value — only who called and what they hit.
            log.warn(
                    "internal_auth.rejected remoteAddr={} uri={}",
                    request.getRemoteAddr(),
                    request.getRequestURI());
            writeUnauthorized(response);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Constant-time compare; a {@code null} or blank header can never match. */
    private boolean tokenMatches(String provided) {
        if (provided == null || provided.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expectedToken);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getWriter().write(json.writeValueAsString(UNAUTHORIZED));
    }
}
