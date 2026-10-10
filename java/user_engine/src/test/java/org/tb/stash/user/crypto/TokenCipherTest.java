package org.tb.stash.user.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tb.stash.user.config.TokenEncryptionProperties;

class TokenCipherTest {

    private static final String SAMPLE_REFRESH_TOKEN = "1//09abcREFRESH-token-from-google";
    private static final String SAMPLE_ACCESS_TOKEN =
            "ya29.a0AfH6SMBaCCESS-token-from-google-abcdef123";

    private TokenCipher cipher;
    private TokenCipher otherKeyCipher;

    @BeforeEach
    void setUp() {
        cipher = new TokenCipher(new TokenEncryptionProperties(freshBase64Key()));
        otherKeyCipher = new TokenCipher(new TokenEncryptionProperties(freshBase64Key()));
    }

    @Test
    void roundTrip_refreshToken_recoversPlaintext() {
        Ciphertext box = cipher.encrypt(SAMPLE_REFRESH_TOKEN);
        assertThat(box.nonce()).hasSize(12);
        assertThat(box.ciphertext()).isNotEmpty();
        assertThat(cipher.decrypt(box)).isEqualTo(SAMPLE_REFRESH_TOKEN);
    }

    @Test
    void roundTrip_accessToken_recoversPlaintext() {
        Ciphertext box = cipher.encrypt(SAMPLE_ACCESS_TOKEN);
        assertThat(cipher.decrypt(box.ciphertext(), box.nonce())).isEqualTo(SAMPLE_ACCESS_TOKEN);
    }

    @Test
    void encrypt_samePlaintextTwice_yieldsDifferentCiphertextAndNonce() {
        Ciphertext a = cipher.encrypt(SAMPLE_REFRESH_TOKEN);
        Ciphertext b = cipher.encrypt(SAMPLE_REFRESH_TOKEN);
        assertThat(a.nonce()).isNotEqualTo(b.nonce());
        assertThat(a.ciphertext()).isNotEqualTo(b.ciphertext());
    }

    @Test
    void encrypt_thousandCalls_yieldThousandDistinctNonces() {
        Set<String> nonces = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            nonces.add(Base64.getEncoder().encodeToString(cipher.encrypt("payload-" + i).nonce()));
        }
        assertThat(nonces).hasSize(1000);
    }

    @Test
    void decrypt_withTamperedCiphertext_throws() {
        Ciphertext box = cipher.encrypt(SAMPLE_REFRESH_TOKEN);
        box.ciphertext()[0] ^= 0x01;

        assertThatThrownBy(() -> cipher.decrypt(box.ciphertext(), box.nonce()))
                .isInstanceOf(TokenCipherException.class);
    }

    @Test
    void decrypt_withWrongKey_throws() {
        Ciphertext box = cipher.encrypt(SAMPLE_REFRESH_TOKEN);

        assertThatThrownBy(() -> otherKeyCipher.decrypt(box.ciphertext(), box.nonce()))
                .isInstanceOf(TokenCipherException.class);
    }

    @Test
    void construct_withShortKey_throws() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new TokenCipher(new TokenEncryptionProperties(shortKey)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String freshBase64Key() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }
}
