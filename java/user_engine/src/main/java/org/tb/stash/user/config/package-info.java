/**
 * {@code @ConfigurationProperties} beans and framework configuration.
 *
 * <p>Holds the three externalised-config beans ({@code HiemdallProperties}, {@code
 * SessionProperties}, {@code TokenEncryptionProperties}) plus the CORS configuration. Fail-fast
 * validation lives on each bean; missing env vars surface as startup failures with a clear message.
 */
package org.tb.stash.user.config;
