package org.tb.stash.user.crypto;

/**
 * Raised when AES-GCM encryption or decryption fails (bad key, tampered ciphertext, malformed
 * nonce, provider-side crypto error).
 *
 * <p>Deliberately a {@link RuntimeException}: callers cannot usefully recover from a cipher failure
 * other than failing the request.
 */
@SuppressWarnings("serial")
public class TokenCipherException extends RuntimeException {

    public TokenCipherException(String message, Throwable cause) {
        super(message, cause);
    }
}
