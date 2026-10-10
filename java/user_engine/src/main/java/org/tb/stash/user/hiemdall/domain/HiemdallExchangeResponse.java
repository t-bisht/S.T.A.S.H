package org.tb.stash.user.hiemdall.domain;

/**
 * Shape of Hiemdall's {@code POST /internal/auth/exchange} response body.
 *
 * <p>Resolved per spec D4 as {@code {tokens, identity}}.
 */
public record HiemdallExchangeResponse(OAuthTokenBundle tokens, IdentityClaims identity) {}
