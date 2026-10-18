package org.tb.stash.user.tokenrefresh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class IssuedAccessTokenTest {

    @Test
    void toString_masksAccessToken() {
        Instant expiresAt = Instant.parse("2026-10-16T10:00:00Z");
        IssuedAccessToken token = new IssuedAccessToken("ya29.secret-access", expiresAt);

        assertThat(token.toString())
                .doesNotContain("ya29", "secret-access")
                .contains("expiresAt=" + expiresAt);
    }
}
