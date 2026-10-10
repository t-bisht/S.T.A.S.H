package org.tb.stash.user.login.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.tb.stash.user.crypto.TokenCipherException;
import org.tb.stash.user.hiemdall.exception.HandoffExpiredException;
import org.tb.stash.user.hiemdall.exception.HiemdallUnavailableException;
import org.tb.stash.user.session.exception.SessionExpiredException;

/**
 * Maps typed auth-domain exceptions to HTTP responses with a machine-readable {@code {code,
 * message}} body.
 */
@RestControllerAdvice
public class AuthErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(AuthErrorAdvice.class);

    public record ErrorBody(String code, String message) {}

    @ExceptionHandler(HandoffExpiredException.class)
    public ResponseEntity<ErrorBody> onHandoffExpired(HandoffExpiredException e) {
        log.info("auth.handoff.rejected reason=\"{}\"", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorBody("handoff_expired", "Handoff code expired or already used."));
    }

    @ExceptionHandler(SessionExpiredException.class)
    public ResponseEntity<ErrorBody> onSessionExpired(SessionExpiredException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorBody("session_expired", "No active session."));
    }

    @ExceptionHandler(HiemdallUnavailableException.class)
    public ResponseEntity<ErrorBody> onHiemdallDown(HiemdallUnavailableException e) {
        log.warn("auth.hiemdall.unavailable reason=\"{}\"", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorBody("hiemdall_unavailable", "Upstream auth service unavailable."));
    }

    @ExceptionHandler(TokenCipherException.class)
    public ResponseEntity<ErrorBody> onCipherFailure(TokenCipherException e) {
        log.error("auth.cipher.failure", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorBody("cipher_failure", "Internal error."));
    }
}
