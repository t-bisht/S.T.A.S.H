/**
 * "I remember you" layer: translates the {@code STASH_SESSION} cookie into an authenticated
 * principal on every request.
 *
 * <p>Not responsible for minting sessions (that's {@code login}). This package owns the per-request
 * cookie-to-principal filter, the opaque handle generator, and the cookie builder used by {@code
 * login} to produce {@code Set-Cookie} headers on mint and clear.
 *
 * <p>Depends on {@code config}, {@code persistence}, {@code domain}.
 */
package org.tb.stash.user.session;
