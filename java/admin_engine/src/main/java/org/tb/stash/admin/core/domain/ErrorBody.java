package org.tb.stash.admin.core.domain;

/**
 * Flat error response body — the same {@code {code, message}} shape user_engine already returns
 * ({@code AuthErrorAdvice.ErrorBody}). Top-level record, not nested.
 *
 * @param code stable machine-readable code (api.md §14).
 * @param message human-readable summary — never the raw exception text on the 500 path.
 */
public record ErrorBody(String code, String message) {}
