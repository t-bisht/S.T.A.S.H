/**
 * Outbound adapter for the back-channel calls to Hiemdall.
 *
 * <p>Owns both calls STASH makes to Hiemdall: {@code POST /internal/auth/exchange} to redeem a
 * handoff code for the Google token bundle + verified identity claims ({@code
 * HiemdallExchangeClient}), and {@code POST /internal/auth/google/refresh} to swap a stored refresh
 * token for a new access token ({@code HiemdallRefreshClient}). STASH never talks to Google
 * directly; Hiemdall is the trust boundary.
 *
 * <p>Also owns the wire-shape records of those calls ({@code HiemdallExchangeResponse}, {@code
 * OAuthTokenBundle}, {@code IdentityClaims}, {@code HiemdallRefreshResponse}, {@code
 * HiemdallErrorResponse}), the {@code RefreshOutcome} result type, and the two typed exchange
 * exceptions mapped by {@code login.AuthErrorAdvice}.
 *
 * <p>Depends on {@code config}, and {@code crypto} for token masking only.
 */
package org.tb.stash.user.hiemdall;
