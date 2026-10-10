/**
 * {@code GET /me} — current authenticated user, for SPA bootstrap routing.
 *
 * <p>Reads the {@link org.tb.stash.user.session.AuthenticatedPrincipal} that {@code
 * SessionCookieFilter} placed on the request; translates to a thin response record. Throws {@code
 * SessionExpiredException} (handled by {@code login.AuthErrorAdvice}) if absent.
 *
 * <p>Depends on {@code session}, {@code domain} (indirectly via the principal).
 */
package org.tb.stash.user.me;
