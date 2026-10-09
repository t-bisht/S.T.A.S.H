package org.tb.stash.user.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * AES-GCM key material for refresh-token at-rest encryption.
 *
 * @param encKey base64-encoded 32-byte AES key; bound from env {@code STASH_TOKEN_ENC_KEY}.
 *     Generate with {@code openssl rand -base64 32}.
 */
@Validated
@ConfigurationProperties(prefix = "tokens")
public record TokenEncryptionProperties(@NotBlank String encKey) {}
