package org.tb.stash.user.tokenrefresh.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.tb.stash.user.tokenrefresh.controller.TokenRefreshController;
import org.tb.stash.user.tokenrefresh.domain.InternalErrorBody;

/**
 * Maps token-endpoint exceptions to the engine-facing {@code {error, error_description}} body (spec
 * 02, §5.1), scoped to {@link TokenRefreshController} so it never shadows the SPA's {@code
 * AuthErrorAdvice}. Highest precedence so {@code MethodArgumentTypeMismatchException} is caught
 * here rather than by any default handler. Every response carries {@code Cache-Control: no-store};
 * 503 adds {@code Retry-After: 60}.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = TokenRefreshController.class)
public class TokenRefreshAdvice {

    private static final Logger log = LoggerFactory.getLogger(TokenRefreshAdvice.class);

    private static final String RETRY_AFTER_SECONDS = "60";

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<InternalErrorBody> onBadUserId(MethodArgumentTypeMismatchException e) {
        log.info("internal_token.invalid_request param={}", e.getName());
        return error(HttpStatus.BAD_REQUEST, "invalid_request", "userId must be a UUID");
    }

    @ExceptionHandler(UnsupportedProviderException.class)
    public ResponseEntity<InternalErrorBody> onUnsupportedProvider(UnsupportedProviderException e) {
        log.info("internal_token.unsupported_provider");
        return error(HttpStatus.BAD_REQUEST, "unsupported_provider", "provider is not supported");
    }

    @ExceptionHandler(OAuthAccountNotFoundException.class)
    public ResponseEntity<InternalErrorBody> onAccountNotFound(OAuthAccountNotFoundException e) {
        log.info("internal_token.account_not_found userId={}", e.userId());
        return error(
                HttpStatus.NOT_FOUND, "account_not_found", "no linked Google account for user");
    }

    @ExceptionHandler(LoginRequiredException.class)
    public ResponseEntity<InternalErrorBody> onLoginRequired(LoginRequiredException e) {
        log.info("internal_token.login_required userId={}", e.userId());
        return error(HttpStatus.CONFLICT, "login_required", "user must sign in again");
    }

    @ExceptionHandler(RefreshUnavailableException.class)
    public ResponseEntity<InternalErrorBody> onRefreshUnavailable(RefreshUnavailableException e) {
        // The underlying cause was already logged by the service; record only that we 503'd.
        log.warn("internal_token.refresh_unavailable reason=\"{}\"", e.reason());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(
                        new InternalErrorBody(
                                "refresh_unavailable", "token refresh temporarily unavailable"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<InternalErrorBody> onUnexpected(Exception e) {
        log.error("internal_token.internal_error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "internal error");
    }

    private static ResponseEntity<InternalErrorBody> error(
            HttpStatusCode status, String code, String description) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(new InternalErrorBody(code, description));
    }
}
