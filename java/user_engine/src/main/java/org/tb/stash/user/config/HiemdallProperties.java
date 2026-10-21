package org.tb.stash.user.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Externalised configuration for the back-channel calls to Hiemdall's {@code
 * /internal/auth/exchange} and {@code /internal/auth/google/refresh} endpoints.
 *
 * @param baseUrl Hiemdall root URL (e.g. {@code http://localhost:9082}).
 * @param appId app id Hiemdall knows us by (v1: {@code "stash"}).
 * @param internalToken shared secret sent as {@code X-Internal-Auth} on the outbound calls to
 *     Hiemdall. Bound from env {@code HIEMDALL_INTERNAL_TOKEN}; must match Hiemdall side exactly.
 *     Distinct from {@link InternalAuthProperties#svcToken()}, the inbound guard — different trust
 *     boundary, rotated independently.
 * @param exchangePath path under {@code baseUrl} for the exchange endpoint.
 * @param refreshPath path under {@code baseUrl} for the Google access-token refresh endpoint.
 */
@Validated
@ConfigurationProperties(prefix = "hiemdall")
public record HiemdallProperties(
        @NotNull URI baseUrl,
        @NotBlank String appId,
        @NotBlank String internalToken,
        @NotBlank String exchangePath,
        @NotBlank String refreshPath) {}
