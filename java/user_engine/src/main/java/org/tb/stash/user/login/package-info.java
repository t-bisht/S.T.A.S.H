/**
 * Login + logout flow: {@code POST /auth/session} and {@code POST /auth/logout}.
 *
 * <p>Orchestrates handoff redemption against {@code hiemdall}, encrypts the resulting token pair
 * via {@code crypto}, upserts the identity rows through {@link
 * org.tb.stash.user.login.crud.IdentityUpsertService}, and mints the opaque session handle via the
 * {@code session} package's cookie builder. Also owns the {@code @RestControllerAdvice} that maps
 * typed exceptions from {@code hiemdall} / {@code session} / {@code crypto} to HTTP responses.
 *
 * <p>Depends on {@code hiemdall}, {@code crypto}, {@code persistence}, {@code domain}, {@code
 * session}, {@code config}.
 */
package org.tb.stash.user.login;
