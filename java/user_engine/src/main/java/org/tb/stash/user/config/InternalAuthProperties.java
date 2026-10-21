package org.tb.stash.user.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Shared secret that guards user_engine's {@code /internal/**} endpoints (spec 02).
 *
 * <p>Callers (Stash backend engines) present the value as the {@code X-Internal-Auth} header;
 * {@code InternalAuthFilter} compares it in constant time. This is the inbound guard only; the
 * outbound secret user_engine sends to Hiemdall ({@link HiemdallProperties#internalToken()}) is a
 * separate value bound from a separate env var, so a leak or rotation of one never touches the
 * other.
 *
 * @param svcToken shared secret; bound from env {@code INTERNAL_SVC_TOKEN}. {@code @NotBlank} so
 *     the service fails to start rather than exposing {@code /internal/**} with an empty secret.
 */
@Validated
@ConfigurationProperties(prefix = "internal-auth")
public record InternalAuthProperties(@NotBlank String svcToken) {}
