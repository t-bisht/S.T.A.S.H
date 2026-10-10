package org.tb.stash.user.me;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tb.stash.user.session.AuthenticatedPrincipal;
import org.tb.stash.user.session.SessionExpiredException;

/** {@code GET /me} — current authenticated user, for SPA bootstrap routing. */
@RestController
public class MeController {

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(HttpServletRequest request) {
        AuthenticatedPrincipal principal =
                (AuthenticatedPrincipal)
                        request.getAttribute(AuthenticatedPrincipal.ATTRIBUTE_NAME);
        if (principal == null) {
            throw new SessionExpiredException("no session");
        }
        return ResponseEntity.ok(
                new MeResponse(
                        principal.userId(),
                        principal.email(),
                        principal.displayName(),
                        principal.picture()));
    }
}
