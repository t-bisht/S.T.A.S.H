/**
 * Outbound adapter for the back-channel exchange with Hiemdall.
 *
 * <p>Owns the single call STASH makes to Hiemdall: {@code POST /internal/auth/exchange} to redeem a
 * handoff code for the Google token bundle + verified identity claims. STASH does not talk to
 * Google directly in Phase 2; Hiemdall is the trust boundary.
 *
 * <p>Also owns the wire-shape records returned by that call ({@code HiemdallExchangeResponse},
 * {@code OAuthTokenBundle}, {@code IdentityClaims}) and the two typed exceptions mapped by {@code
 * login.AuthErrorAdvice}.
 *
 * <p>Depends on {@code config} only.
 */
package org.tb.stash.user.hiemdall;
