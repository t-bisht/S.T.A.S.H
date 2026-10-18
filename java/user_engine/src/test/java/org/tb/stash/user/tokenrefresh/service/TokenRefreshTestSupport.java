package org.tb.stash.user.tokenrefresh.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tb.stash.user.config.TokenRefreshProperties;
import org.tb.stash.user.core.crud.UserOAuthAccountRepository;
import org.tb.stash.user.core.domain.UserOAuthAccount;
import org.tb.stash.user.crypto.Ciphertext;
import org.tb.stash.user.crypto.TokenCipher;
import org.tb.stash.user.hiemdall.domain.HiemdallRefreshResponse;
import org.tb.stash.user.hiemdall.domain.RefreshOutcome;
import org.tb.stash.user.hiemdall.service.HiemdallRefreshClient;
import org.tb.stash.user.tokenrefresh.crud.TokenRefreshPersistence;

/**
 * Shared mocks, stubs and row fixtures for the token-refresh service tests. Subclasses wire their
 * bean under test on top of a real {@link TokenExchanger} built from these mocks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
abstract class TokenRefreshTestSupport {

    static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");
    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final String REFRESH_TOKEN = "1//refresh";
    static final String STORED_ACCESS_TOKEN = "ya29.stored";
    static final Ciphertext NEW_ACCESS_BOX = new Ciphertext(new byte[] {1}, new byte[] {2});

    static final TokenRefreshProperties PROPS =
            new TokenRefreshProperties(
                    true,
                    Duration.ofMinutes(10),
                    Duration.ofMinutes(1),
                    Duration.ofMinutes(15),
                    2,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(10));

    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock UserOAuthAccountRepository accounts;
    @Mock TokenRefreshPersistence persistence;
    @Mock HiemdallRefreshClient hiemdall;
    @Mock TokenCipher cipher;
    @Mock AccountLocks locks;

    SimpleMeterRegistry registry;
    TokenRefreshMetrics metrics;
    TokenExchanger exchanger;

    @BeforeEach
    void setUpSupport() {
        registry = new SimpleMeterRegistry();
        metrics = new TokenRefreshMetrics(registry);
        exchanger = new TokenExchanger(persistence, hiemdall, cipher, metrics, CLOCK);

        when(locks.tryLock(any(), any())).thenReturn(true);
        when(cipher.decrypt(any(byte[].class), any(byte[].class)))
                .thenAnswer(inv -> new String((byte[]) inv.getArgument(0), StandardCharsets.UTF_8));
        when(cipher.encrypt(anyString())).thenReturn(NEW_ACCESS_BOX);
        when(persistence.storeRefreshed(any(), any(), any(), any(), any(), any())).thenReturn(true);
    }

    /** Row whose decrypt (via the stubbed cipher) yields REFRESH_TOKEN / STORED_ACCESS_TOKEN. */
    static UserOAuthAccount account(Instant expiresAt, Instant lastRefreshedAt) {
        return account(expiresAt, lastRefreshedAt, REFRESH_TOKEN);
    }

    /**
     * Row whose refresh token decrypts to {@code refreshToken}. Mockito matches {@code byte[]}
     * arguments by content, so rows meant to behave differently need distinct plaintext.
     */
    static UserOAuthAccount account(
            Instant expiresAt, Instant lastRefreshedAt, String refreshToken) {
        return new UserOAuthAccount(
                UUID.randomUUID(),
                USER_ID,
                UserOAuthAccount.PROVIDER_GOOGLE,
                "sub-1",
                "u@example.com",
                "openid",
                refreshToken.getBytes(StandardCharsets.UTF_8),
                new byte[12],
                STORED_ACCESS_TOKEN.getBytes(StandardCharsets.UTF_8),
                new byte[12],
                expiresAt,
                NOW.minus(Duration.ofDays(1)),
                false,
                lastRefreshedAt,
                null);
    }

    static UserOAuthAccount withExpiry(
            UserOAuthAccount a, Instant expiresAt, Instant lastRefreshedAt) {
        return new UserOAuthAccount(
                a.oauthAccountId(),
                a.userId(),
                a.provider(),
                a.providerSub(),
                a.providerEmail(),
                a.scope(),
                a.refreshTokenEnc(),
                a.refreshTokenNonce(),
                a.accessTokenEnc(),
                a.accessTokenNonce(),
                expiresAt,
                a.linkedAt(),
                a.loginRequired(),
                lastRefreshedAt,
                a.lastRefreshError());
    }

    static UserOAuthAccount flagged(UserOAuthAccount a) {
        return new UserOAuthAccount(
                a.oauthAccountId(),
                a.userId(),
                a.provider(),
                a.providerSub(),
                a.providerEmail(),
                a.scope(),
                a.refreshTokenEnc(),
                a.refreshTokenNonce(),
                a.accessTokenEnc(),
                a.accessTokenNonce(),
                a.accessTokenExpiresAt(),
                a.linkedAt(),
                true,
                a.lastRefreshedAt(),
                "reauth_required");
    }

    static RefreshOutcome success(String accessToken, String rotatedRefreshToken) {
        return RefreshOutcome.succeeded(
                new HiemdallRefreshResponse(
                        accessToken, rotatedRefreshToken, 3599L, "openid", "Bearer"));
    }

    double counter(String trigger, String outcome) {
        var c =
                registry.find(TokenRefreshMetrics.COUNTER)
                        .tag("trigger", trigger)
                        .tag("outcome", outcome)
                        .counter();
        return c == null ? 0.0 : c.count();
    }
}
