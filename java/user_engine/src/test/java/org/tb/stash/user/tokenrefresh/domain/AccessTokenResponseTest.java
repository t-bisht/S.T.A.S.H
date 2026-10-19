package org.tb.stash.user.tokenrefresh.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccessTokenResponseTest {

    @Test
    void from_setsBearerTypeAndCopiesTokenAndExpiry() {
        Instant expiresAt = Instant.parse("2026-10-16T10:00:00Z");

        AccessTokenResponse response =
                AccessTokenResponse.from(new IssuedAccessToken("ya29.secret", expiresAt));

        assertThat(response.accessToken()).isEqualTo("ya29.secret");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void toString_masksTheToken() {
        AccessTokenResponse response =
                AccessTokenResponse.from(
                        new IssuedAccessToken(
                                "ya29.secret", Instant.parse("2026-10-16T10:00:00Z")));

        assertThat(response.toString()).doesNotContain("ya29.secret").contains("****");
    }
}
