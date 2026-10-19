package org.tb.stash.user.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Shared secret that guards user_engine's {@code /internal/**} endpoints (spec 02).
 *
 * <p>Callers (Stash backend engines) present the value as the {@code X-Internal-Auth} header;
 * {@code InternalAuthFilter} compares it in constant time. The same secret backs the back-channel
 * calls user_engine makes to Hiemdall ({@link HiemdallProperties#internalToken()}); only its
 * direction differs, so it is bound from the same env var.
 *
 * @param svcToken shared secret; bound from env {@code INTERNAL_SVC_TOKEN}. {@code @NotBlank} so
 *     the service fails to start rather than exposing {@code /internal/**} with an empty secret.
 */
@Validated
@ConfigurationProperties(prefix = "internal-auth")
public record InternalAuthProperties(@NotBlank String svcToken) {}
