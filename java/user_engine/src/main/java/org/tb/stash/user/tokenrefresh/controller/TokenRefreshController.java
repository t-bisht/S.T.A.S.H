package org.tb.stash.user.tokenrefresh.controller;

import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tb.stash.user.core.domain.UserOAuthAccount;
import org.tb.stash.user.tokenrefresh.domain.AccessTokenResponse;
import org.tb.stash.user.tokenrefresh.domain.IssuedAccessToken;
import org.tb.stash.user.tokenrefresh.exception.UnsupportedProviderException;
import org.tb.stash.user.tokenrefresh.service.TokenRefreshService;

/**
 * Internal, server-to-server token endpoints for Stash backend engines (spec 02). Never called by
 * the SPA; reachability is gated by {@link InternalAuthFilter}. Exceptions are mapped to JSON by
 * {@link org.tb.stash.user.tokenrefresh.exception.TokenRefreshAdvice}.
 *
 * <p>Both endpoints validate {@code provider} before touching the service and carry {@code
 * Cache-Control: no-store}. Any request body is ignored.
 */
@RestController
@RequestMapping("/internal/users/{userId}/oauth/{provider}")
public class TokenRefreshController {

    private final TokenRefreshService service;

    public TokenRefreshController(TokenRefreshService service) {
        this.service = service;
    }

    /**
     * Normal path: hands the engine a token valid beyond {@code min-validity}, refreshing inline if
     * the stored one is about to expire.
     */
    @GetMapping("/access-token")
    public ResponseEntity<AccessTokenResponse> accessToken(
            @PathVariable UUID userId, @PathVariable String provider) {
        requireGoogle(provider);
        return noStore(service.getAccessToken(userId));
    }

    /** Forces a refresh after the engine saw a Gmail 401 on the current token. */
    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @PathVariable UUID userId, @PathVariable String provider) {
        requireGoogle(provider);
        return noStore(service.refreshForUser(userId));
    }

    private static void requireGoogle(String provider) {
        if (!UserOAuthAccount.PROVIDER_GOOGLE.equals(provider)) {
            throw new UnsupportedProviderException(provider);
        }
    }

    private static ResponseEntity<AccessTokenResponse> noStore(IssuedAccessToken token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AccessTokenResponse.from(token));
    }
}
