package org.tb.stash.user.session.service;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Produces opaque session handles for the {@code sessions.session_handle} column.
 *
 * <p>Yields base64url strings backed by 32 bytes (256 bits) of {@link SecureRandom} entropy — meets
 * the spec's "≥32 bytes" bar and avoids any padding characters in the cookie.
 */
@Component
public class SessionHandleGenerator {

    private static final int ENTROPY_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public String generate() {
        byte[] buf = new byte[ENTROPY_BYTES];
        random.nextBytes(buf);
        return encoder.encodeToString(buf);
    }
}
