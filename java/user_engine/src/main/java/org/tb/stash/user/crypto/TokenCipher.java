package org.tb.stash.user.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.tb.stash.user.config.TokenEncryptionProperties;

/**
 * AES-256-GCM wrapper for the stored OAuth tokens.
 *
 * <p>Encrypts both the refresh token and the access token with the same key (sourced from {@code
 * STASH_TOKEN_ENC_KEY} via {@link TokenEncryptionProperties}). A fresh 12-byte random nonce is
 * produced per encryption; callers must persist the nonce alongside the ciphertext — decryption
 * requires both.
 *
 * <p>Thread-safety: stateless once constructed; the only mutable state is a {@link SecureRandom}
 * instance which is itself thread-safe.
 *
 * <p>{@link #decrypt(byte[], byte[])} is defined for completeness; Phase 2 has no production
 * caller. The future refresh client (deferred feature) will exercise it.
 */
@Component
public class TokenCipher {

    /** 128-bit authentication tag — maximum size supported by GCM. */
    private static final int GCM_TAG_BITS = 128;

    /** Standard GCM nonce size (NIST SP 800-38D §8.2.1 recommended). */
    private static final int GCM_NONCE_BYTES = 12;

    private static final String ALG_SPEC = "AES/GCM/NoPadding";
    private static final String KEY_ALG = "AES";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(TokenEncryptionProperties props) {
        byte[] raw = Base64.getDecoder().decode(props.encKey());
        if (raw.length != 32) {
            throw new IllegalStateException(
                    "tokens.enc-key must decode to 32 bytes (AES-256); got " + raw.length);
        }
        this.key = new SecretKeySpec(raw, KEY_ALG);
    }

    /** Encrypts the UTF-8 bytes of {@code plaintext}; returns ciphertext + fresh 12-byte nonce. */
    public Ciphertext encrypt(String plaintext) {
        byte[] nonce = new byte[GCM_NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(ALG_SPEC);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new Ciphertext(ciphertext, nonce);
        } catch (GeneralSecurityException e) {
            throw new TokenCipherException("token encrypt failed", e);
        }
    }

    /** Decrypts; throws {@link TokenCipherException} on bad key, tamper, or wrong nonce. */
    public String decrypt(byte[] ciphertext, byte[] nonce) {
        try {
            Cipher cipher = Cipher.getInstance(ALG_SPEC);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] plain = cipher.doFinal(ciphertext);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new TokenCipherException("token decrypt failed", e);
        }
    }

    /** Convenience overload for a {@link Ciphertext} box. */
    public String decrypt(Ciphertext box) {
        return decrypt(box.ciphertext(), box.nonce());
    }
}
