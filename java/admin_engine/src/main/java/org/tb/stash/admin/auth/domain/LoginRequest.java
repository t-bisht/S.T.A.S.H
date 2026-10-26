package org.tb.stash.admin.auth.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /admin/login} request body.
 *
 * @param username admin username.
 * @param password admin password.
 */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
