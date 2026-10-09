package org.tb.stash.user.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Externalised configuration for the back-channel call to Hiemdall's {@code
 * /internal/auth/exchange} endpoint.
 *
 * @param baseUrl Hiemdall root URL (e.g. {@code http://localhost:9082}).
 * @param appId app id Hiemdall knows us by (v1: {@code "stash"}).
 * @param internalToken shared secret sent as {@code X-Internal-Auth}. Bound from env {@code
 *     INTERNAL_SVC_TOKEN}; must match Hiemdall side exactly.
 * @param exchangePath path under {@code baseUrl} for the exchange endpoint.
 */
@Validated
@ConfigurationProperties(prefix = "hiemdall")
public record HiemdallProperties(
        @NotNull URI baseUrl,
        @NotBlank String appId,
        @NotBlank String internalToken,
        @NotBlank String exchangePath) {}
