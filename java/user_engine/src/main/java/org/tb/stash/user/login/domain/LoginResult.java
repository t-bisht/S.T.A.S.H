package org.tb.stash.user.login.domain;

/** Cookie value + response body for a successful login. */
public record LoginResult(String sessionHandle, LoginResponse body) {}
