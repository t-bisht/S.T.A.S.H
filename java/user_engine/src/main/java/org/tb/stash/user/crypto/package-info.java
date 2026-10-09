/**
 * At-rest encryption primitives for sensitive credentials.
 *
 * <p>Scope today: AES-256-GCM wrapper for the stored OAuth refresh and access tokens. Keys are
 * supplied externally via {@link org.tb.stash.user.config.TokenEncryptionProperties}.
 *
 * <p>Depends on {@code config} only.
 */
package org.tb.stash.user.crypto;
