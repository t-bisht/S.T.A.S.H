package org.tb.stash.user.tokenrefresh.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Error body for the {@code /internal/**} token endpoints (spec 02).
 *
 * <p>Uses the {@code {error, error_description}} shape engines already parse from Hiemdall, not the
 * SPA-facing {@code {code, message}} of {@code AuthErrorAdvice}.
 *
 * @param error stable machine code, e.g. {@code login_required}
 * @param errorDescription fixed human-readable text; never carries token data
 */
public record InternalErrorBody(
        @JsonProperty("error") String error,
        @JsonProperty("error_description") String errorDescription) {}
