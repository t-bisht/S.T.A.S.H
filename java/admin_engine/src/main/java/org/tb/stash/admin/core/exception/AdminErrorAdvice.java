package org.tb.stash.admin.core.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.tb.stash.admin.catalog.exception.DuplicateNameException;
import org.tb.stash.admin.catalog.exception.InvalidSubcategoryException;
import org.tb.stash.admin.catalog.exception.NotFoundException;
import org.tb.stash.admin.core.domain.ErrorBody;

/**
 * Maps admin-domain exceptions to HTTP responses with the flat {@code {code, message}} body (api.md
 * §14). One global advice for the login + catalog surfaces.
 *
 * <p>The {@code 401 unauthorized} path is NOT here: {@code SessionInterceptor} runs before the
 * dispatcher and writes its own {@link ErrorBody}, and {@code AuthController} writes the login
 * mismatch directly. Both serialise {@link ErrorBody} so the shape stays in sync.
 */
@RestControllerAdvice
public class AdminErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(AdminErrorAdvice.class);

    /** Bean-validation failure — surface the first field's message so the UI can show it. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> onBodyValidation(MethodArgumentNotValidException e) {
        String message =
                e.getBindingResult().getFieldError() != null
                        ? e.getBindingResult().getFieldError().getDefaultMessage()
                        : "Request body failed validation.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorBody("validation_error", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> onIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorBody("validation_error", e.getMessage()));
    }

    @ExceptionHandler(InvalidSubcategoryException.class)
    public ResponseEntity<ErrorBody> onInvalidSubcategory(InvalidSubcategoryException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorBody("invalid_subcategory", e.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorBody> onNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorBody("not_found", e.getMessage()));
    }

    @ExceptionHandler(DuplicateNameException.class)
    public ResponseEntity<ErrorBody> onDuplicateName(DuplicateNameException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorBody("duplicate_name", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> onUnhandled(Exception e) {
        log.error("admin.unhandled_exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorBody("internal_error", "Internal error."));
    }
}
