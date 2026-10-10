package org.tb.stash.user.login.domain;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /auth/session} body — the opaque Hiemdall handoff code. */
public record LoginRequest(@NotBlank String handoff) {}
