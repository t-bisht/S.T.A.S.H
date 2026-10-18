package org.tb.stash.user.tokenrefresh.crud;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.tb.stash.user.core.crud.SessionRepository;
import org.tb.stash.user.core.crud.UserOAuthAccountRepository;
import org.tb.stash.user.core.crud.UserRepository;
import org.tb.stash.user.core.domain.Session;
import org.tb.stash.user.core.domain.User;
import org.tb.stash.user.core.domain.UserOAuthAccount;
import org.tb.stash.user.crypto.Ciphertext;
import org.tb.stash.user.hiemdall.domain.IdentityClaims;
import org.tb.stash.user.login.crud.IdentityUpsertService;
import org.tb.stash.user.tokenrefresh.domain.RefreshFailureCode;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * Runs the token-refresh queries and writes against real Postgres initialised with the production
 * {@code pg-init} scripts ({@code 02_users.sql} + {@code 03_token_refresh.sql}).
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TokenRefreshPersistence.class, IdentityUpsertService.class})
@Testcontainers
class TokenRefreshPersistenceIT {

    /** {@code pg-init} relative to the user_engine project dir (Gradle's test working dir). */
    private static final Path PG_INIT = Path.of("../../infra/self_hosted/pg-init");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withCopyFileToContainer(
                            MountableFile.forHostPath(PG_INIT), "/docker-entrypoint-initdb.d/");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** Postgres stores microseconds; truncate so round-tripped instants compare equal. */
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    private static final Instant THRESHOLD = NOW.plus(Duration.ofMinutes(15));

    @Autowired private TokenRefreshPersistence persistence;
    @Autowired private IdentityUpsertService identityUpsert;
    @Autowired private UserOAuthAccountRepository accounts;
    @Autowired private UserRepository users;
    @Autowired private SessionRepository sessions;

    // ─── Q1 findDueForRefresh ───────────────────────────────────────────────

    @Test
    void findDueForRefresh_keysetPagingReturnsEachDueRowOnceAndSkipsFlagged() {
        List<UUID> due = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            due.add(newAccount(NOW.plusSeconds(60L * i)).oauthAccountId());
        }
        // Same expiry as an existing row: the id tiebreak must still page it exactly once.
        due.add(newAccount(NOW).oauthAccountId());
        UUID flagged = newAccount(NOW.plusSeconds(30)).oauthAccountId();
        accounts.markLoginRequired(flagged);
        newAccount(NOW.plus(Duration.ofMinutes(50))); // not due

        List<UUID> seen = new ArrayList<>();
        Instant afterExpiry = Instant.EPOCH;
        UUID afterId = new UUID(0L, 0L);
        List<UserOAuthAccount> page;
        do {
            page = accounts.findDueForRefresh(THRESHOLD, afterExpiry, afterId, 2);
            page.forEach(a -> seen.add(a.oauthAccountId()));
            if (!page.isEmpty()) {
                afterExpiry = page.getLast().accessTokenExpiresAt();
                afterId = page.getLast().oauthAccountId();
            }
        } while (page.size() == 2);

        assertThat(seen).containsExactlyInAnyOrderElementsOf(due).doesNotHaveDuplicates();
        assertThat(seen).doesNotContain(flagged);
    }

    // ─── Q3 / Q4 storeRefreshed ─────────────────────────────────────────────

    @Test
    void storeRefreshed_newerToken_updatesAccessTokenAndBookkeeping() {
        UserOAuthAccount account = newAccount(NOW.plusSeconds(60));
        accounts.recordRefreshError(account.oauthAccountId(), "upstream_unavailable");
        Instant newExpiry = NOW.plus(Duration.ofMinutes(60));

        boolean stored =
                persistence.storeRefreshed(
                        account.oauthAccountId(), box(7), newExpiry, null, null, NOW);

        assertThat(stored).isTrue();
        UserOAuthAccount after = reload(account);
        assertThat(after.accessTokenEnc()).isEqualTo(box(7).ciphertext());
        assertThat(after.accessTokenExpiresAt()).isEqualTo(newExpiry);
        assertThat(after.lastRefreshedAt()).isEqualTo(NOW);
        assertThat(after.lastRefreshError()).isNull();
        assertThat(after.scope()).isEqualTo("openid"); // null scope keeps stored value
        assertThat(after.refreshTokenEnc()).isEqualTo(account.refreshTokenEnc());
    }

    @Test
    void storeRefreshed_withRotatedRefreshToken_replacesRefreshToken() {
        UserOAuthAccount account = newAccount(NOW.plusSeconds(60));

        persistence.storeRefreshed(
                account.oauthAccountId(),
                box(7),
                NOW.plus(Duration.ofMinutes(60)),
                "openid email",
                box(8),
                NOW);

        UserOAuthAccount after = reload(account);
        assertThat(after.refreshTokenEnc()).isEqualTo(box(8).ciphertext());
        assertThat(after.refreshTokenNonce()).isEqualTo(box(8).nonce());
        assertThat(after.scope()).isEqualTo("openid email");
    }

    @Test
    void storeRefreshed_whenStoredExpiryIsNotOlder_updatesNothing() {
        Instant storedExpiry = NOW.plus(Duration.ofMinutes(60));
        UserOAuthAccount account = newAccount(storedExpiry);

        boolean stored =
                persistence.storeRefreshed(
                        account.oauthAccountId(), box(7), storedExpiry, null, null, NOW);

        assertThat(stored).isFalse();
        assertThat(reload(account).accessTokenEnc()).isEqualTo(account.accessTokenEnc());
    }

    @Test
    void storeRefreshed_onFlaggedAccount_updatesNothing() {
        UserOAuthAccount account = newAccount(NOW.plusSeconds(60));
        accounts.markLoginRequired(account.oauthAccountId());

        boolean stored =
                persistence.storeRefreshed(
                        account.oauthAccountId(),
                        box(7),
                        NOW.plus(Duration.ofMinutes(60)),
                        null,
                        null,
                        NOW);

        assertThat(stored).isFalse();
    }

    // ─── Q5 + session delete: forceLogout ───────────────────────────────────

    @Test
    void forceLogout_flagsAccountAndDeletesOnlyThatUsersSessions() {
        UserOAuthAccount victim = newAccount(NOW.plusSeconds(60));
        UserOAuthAccount bystander = newAccount(NOW.plusSeconds(60));
        newSession(victim.userId());
        newSession(victim.userId());
        newSession(bystander.userId());

        int deleted = persistence.forceLogout(victim.oauthAccountId(), victim.userId());

        assertThat(deleted).isEqualTo(2);
        UserOAuthAccount after = reload(victim);
        assertThat(after.loginRequired()).isTrue();
        assertThat(after.lastRefreshError()).isEqualTo("reauth_required");
        assertThat(sessionsOf(victim.userId())).isEmpty();
        assertThat(sessionsOf(bystander.userId())).hasSize(1);
        assertThat(reload(bystander).loginRequired()).isFalse();
    }

    // ─── Q6 recordFailure ───────────────────────────────────────────────────

    @Test
    void recordFailure_writesStableCode() {
        UserOAuthAccount account = newAccount(NOW.plusSeconds(60));

        persistence.recordFailure(account.oauthAccountId(), RefreshFailureCode.DECRYPT_FAILED);

        assertThat(reload(account).lastRefreshError()).isEqualTo("decrypt_failed");
    }

    // ─── Login heals ────────────────────────────────────────────────────────

    @Test
    void relogin_clearsLoginRequiredAndLastRefreshError() {
        UserOAuthAccount account = newAccount(NOW.plusSeconds(60));
        accounts.markLoginRequired(account.oauthAccountId());

        identityUpsert.upsert(
                new IdentityClaims(account.providerSub(), "u@example.com", "U", null, true),
                "openid",
                box(5),
                box(6),
                NOW.plus(Duration.ofMinutes(60)));

        UserOAuthAccount after = reload(account);
        assertThat(after.loginRequired()).isFalse();
        assertThat(after.lastRefreshError()).isNull();
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private UserOAuthAccount newAccount(Instant accessTokenExpiresAt) {
        User user = users.save(User.newUser("u@example.com", "U", null));
        return accounts.save(
                new UserOAuthAccount(
                        null,
                        user.userId(),
                        UserOAuthAccount.PROVIDER_GOOGLE,
                        "sub-" + UUID.randomUUID(),
                        "u@example.com",
                        "openid",
                        box(1).ciphertext(),
                        box(1).nonce(),
                        box(2).ciphertext(),
                        box(2).nonce(),
                        accessTokenExpiresAt,
                        NOW,
                        false,
                        null,
                        null));
    }

    private void newSession(UUID userId) {
        sessions.save(
                new Session(
                        UUID.randomUUID().toString(),
                        userId,
                        NOW,
                        NOW,
                        NOW.plus(Duration.ofDays(1))));
    }

    private List<Session> sessionsOf(UUID userId) {
        return StreamSupport.stream(sessions.findAll().spliterator(), false)
                .filter(s -> s.userId().equals(userId))
                .toList();
    }

    private UserOAuthAccount reload(UserOAuthAccount account) {
        return accounts.findById(account.oauthAccountId()).orElseThrow();
    }

    /** Deterministic fake ciphertext; the DB never interprets it. */
    private static Ciphertext box(int seed) {
        return new Ciphertext(new byte[] {(byte) seed, 1, 2}, new byte[] {(byte) seed, 3});
    }
}
