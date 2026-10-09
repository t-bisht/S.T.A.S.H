package org.tb.stash.user.crypto;

/**
 * AES-GCM encryption output.
 *
 * <p>Carries the ciphertext (which includes the 128-bit authentication tag) together with the fresh
 * 12-byte nonce that was used to produce it. Both halves must be persisted together — decryption
 * fails without the exact nonce.
 */
public record Ciphertext(byte[] ciphertext, byte[] nonce) {}
