/**
 * {@code @ConfigurationProperties} beans and framework configuration.
 *
 * <p>Holds the externalised-config beans ({@code HiemdallProperties}, {@code SessionProperties},
 * {@code TokenEncryptionProperties}, {@code TokenRefreshProperties}) plus the CORS configuration.
 * Fail-fast validation lives on each bean; missing env vars surface as startup failures with a
 * clear message.
 */
package org.tb.stash.user.config;
