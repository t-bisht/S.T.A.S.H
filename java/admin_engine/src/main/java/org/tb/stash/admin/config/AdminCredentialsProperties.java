package org.tb.stash.admin.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Static admin console credentials (v1 auth model — see {@code ../admin_module_design.md §4}).
 *
 * <p>Bound from {@code admin-credentials.*} (env {@code STASH_ADMIN_USERNAME} / {@code
 * STASH_ADMIN_PASSWORD}). {@code @NotBlank} so the service fails to start rather than exposing the
 * admin surface with an empty credential — the same fail-fast pattern as user_engine's {@code
 * InternalAuthProperties}.
 *
 * @param username admin login username
 * @param password admin login password
 */
@Validated
@ConfigurationProperties(prefix = "admin-credentials")
public record AdminCredentialsProperties(@NotBlank String username, @NotBlank String password) {}
