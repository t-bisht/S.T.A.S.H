package org.tb.stash.user.hiemdall.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HiemdallRefreshResponseTest {

    @Test
    void toString_masksAccessAndRefreshTokens() {
        HiemdallRefreshResponse response =
                new HiemdallRefreshResponse(
                        "ya29.secret-access", "1//secret-refresh", 3599L, "openid", "Bearer");

        String rendered = response.toString();

        assertThat(rendered)
                .doesNotContain("ya29", "secret-access", "1//", "secret-refresh")
                .contains("expiresIn=3599", "scope=openid");
    }
}
