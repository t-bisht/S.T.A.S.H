package org.tb.stash.admin.core.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * SPA deep-link fallback (api.md §7.4).
 *
 * <p>Spring Boot serves {@code src/main/resources/static/} at {@code GET /}; this controller
 * forwards any other non-API, non-actuator GET to {@code index.html} so a direct navigation to a
 * client route (e.g. {@code /review}) does not 404 on reload.
 *
 * <p><b>Why the dot-excluding pattern, not a literal {@code /**}.</b> {@code
 * RequestMappingHandlerMapping} is consulted ahead of the static {@code
 * ResourceHttpRequestHandler}, so a bare {@code /**} controller would shadow hashed asset requests
 * ({@code /assets/index-*.js}) and forward them to HTML. Restricting the mapping to a single path
 * segment with no dot ({@code [^.]*}) matches SPA routes only; asset requests and {@code
 * /index.html} carry a dot, match nothing here, and fall through to the resource handler. This
 * keeps the decided {@code @Controller}-forwarding-to-{@code index.html} design (never {@code
 * addViewControllers}) while letting real assets resolve first.
 *
 * <p><b>Why one segment deep.</b> {@code PathPatternParser} (the Spring 6 default) rejects any
 * pattern with data after {@code **}, so {@code /**}{@code /{path:[^.]*}} fails mapping
 * registration at startup. One level is all the console needs: every client route is top-level
 * ({@code /login}, {@code /review}). A deeper route added later needs its own pattern listed here,
 * spelled out segment by segment.
 *
 * <p>{@code /admin/**} and {@code /actuator/**} are excluded so they reach their normal handlers
 * (which return JSON on a 404) instead of the SPA shell.
 */
@Controller
public class SpaFallbackController {

    @GetMapping({"/", "/{path:[^.]*}"})
    public String forward(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/admin/") || uri.startsWith("/actuator/")) {
            // Unmatched API / actuator route — not a SPA path. Let it 404.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "forward:/index.html";
    }
}
