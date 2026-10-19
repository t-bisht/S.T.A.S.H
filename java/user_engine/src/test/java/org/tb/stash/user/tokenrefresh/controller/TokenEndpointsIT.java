package org.tb.stash.user.tokenrefresh.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;
import org.tb.stash.user.core.crud.SessionRepository;
import org.tb.stash.user.core.crud.UserOAuthAccountRepository;
import org.tb.stash.user.core.crud.UserRepository;
import org.tb.stash.user.core.domain.Session;
import org.tb.stash.user.core.domain.User;
import org.tb.stash.user.core.domain.UserOAuthAccount;
import org.tb.stash.user.crypto.Ciphertext;
import org.tb.stash.user.crypto.TokenCipher;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * End-to-end test of both {@code /internal/**} token endpoints against real Postgres (pg-init
 * scripts) and a {@link MockRestServiceServer}-mocked Hiemdall, driven through the full MVC stack
 * (filter + controller + advice). Covers every §5.1 status row with its exact body, the
 * no-store/Retry-After headers, and the seed-time → wire plaintext round-trip.
 */
@SpringBootTest(
        properties = {
            "tokens.enc-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "internal-auth.svc-token=svc-secret",
            "hiemdall.base-url=http://hiemdall.test",
            "hiemdall.app-id=stash",
            "hiemdall.internal-token=hiemdall-secret",
            "token-refresh.enabled=false"
        })
@AutoConfigureMockMvc
@Testcontainers
class TokenEndpointsIT {

    private static final String SECRET = "svc-secret";
    private static final String HEADER = "X-Internal-Auth";
    private static final String REFRESH_URL = "http://hiemdall.test/internal/auth/google/refresh";

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

    @Autowired private MockMvc mvc;
    @Autowired private MockRestServiceServer hiemdall;
    @Autowired private TokenCipher cipher;
    @Autowired private UserRepository users;
    @Autowired private UserOAuthAccountRepository accounts;
    @Autowired private SessionRepository sessions;

    /** Replaces the shared {@link RestClient} with one bound to a {@link MockRestServiceServer}. */
    @TestConfiguration(proxyBeanMethods = false)
    static class MockHiemdall {
        private final RestClient.Builder builder = RestClient.builder();

        @Bean
        MockRestServiceServer hiemdallServer() {
            return MockRestServiceServer.bindTo(builder).build();
        }

        @Bean
        @Primary
        RestClient mockRestClient(MockRestServiceServer server) {
            return builder.build(); // server param forces bindTo to run before build
        }
    }

    @AfterEach
    void tearDown() {
        hiemdall.reset();
    }

    // ─── 200 success + plaintext round-trip ─────────────────────────────────

    @Test
    void getAccessToken_validStoredToken_returnsPlaintextAndNoStore() throws Exception {
        Instant expiresAt = NOW.plus(Duration.ofHours(1));
        UserOAuthAccount account = seedAccount("ya29.seeded", "1//refresh", expiresAt, false);

        mvc.perform(get(accessTokenUrl(account.userId())).header(HEADER, SECRET))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.access_token").value("ya29.seeded"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_at").exists());
    }

    @Test
    void postRefresh_success_returnsFreshTokenAndNoStore() throws Exception {
        UserOAuthAccount account =
                seedAccount("ya29.old", "1//refresh", NOW.plusSeconds(30), false);
        hiemdall.expect(requestTo(REFRESH_URL))
                .andRespond(
                        withSuccess(
                                """
                                {"access_token": "ya29.fresh", "expires_in": 3599,
                                 "scope": "openid", "token_type": "Bearer"}
                                """,
                                MediaType.APPLICATION_JSON));

        mvc.perform(post(refreshUrl(account.userId())).header(HEADER, SECRET))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.access_token").value("ya29.fresh"))
                .andExpect(jsonPath("$.token_type").value("Bearer"));

        hiemdall.verify();
    }

    // ─── 401 unauthorized (filter) ──────────────────────────────────────────

    @Test
    void anyEndpoint_withoutHeader_returns401() throws Exception {
        UserOAuthAccount account = seedAccount("ya29", "1//r", NOW.plusSeconds(60), false);

        mvc.perform(get(accessTokenUrl(account.userId())))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error").value("unauthorized"))
                .andExpect(
                        jsonPath("$.error_description")
                                .value("X-Internal-Auth missing or invalid"));

        mvc.perform(post(refreshUrl(account.userId())).header(HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
    }

    // ─── 400 invalid_request / unsupported_provider ─────────────────────────

    @Test
    void getAccessToken_nonUuidUserId_returns400InvalidRequest() throws Exception {
        mvc.perform(
                        get("/internal/users/not-a-uuid/oauth/google/access-token")
                                .header(HEADER, SECRET))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.error_description").value("userId must be a UUID"));
    }

    @Test
    void getAccessToken_nonGoogleProvider_returns400Unsupported() throws Exception {
        UUID userId = UUID.randomUUID();

        mvc.perform(
                        get("/internal/users/" + userId + "/oauth/microsoft/access-token")
                                .header(HEADER, SECRET))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_provider"))
                .andExpect(jsonPath("$.error_description").value("provider is not supported"));

        // Case-sensitive: "Google" is not "google".
        mvc.perform(
                        get("/internal/users/" + userId + "/oauth/Google/access-token")
                                .header(HEADER, SECRET))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_provider"));
    }

    // ─── 404 account_not_found ──────────────────────────────────────────────

    @Test
    void getAccessToken_unknownUser_returns404() throws Exception {
        mvc.perform(get(accessTokenUrl(UUID.randomUUID())).header(HEADER, SECRET))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("account_not_found"))
                .andExpect(
                        jsonPath("$.error_description").value("no linked Google account for user"));
    }

    // ─── 409 login_required ─────────────────────────────────────────────────

    @Test
    void getAccessToken_flaggedAccount_returns409() throws Exception {
        UserOAuthAccount account = seedAccount("ya29", "1//r", NOW.plus(Duration.ofHours(1)), true);

        mvc.perform(get(accessTokenUrl(account.userId())).header(HEADER, SECRET))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("login_required"))
                .andExpect(jsonPath("$.error_description").value("user must sign in again"));
    }

    @Test
    void postRefresh_alreadyFlagged_returns409WithoutCallingHiemdall() throws Exception {
        UserOAuthAccount account = seedAccount("ya29", "1//r", NOW.plusSeconds(60), true);
        // No hiemdall.expect(...): any outbound call would fail verification.

        mvc.perform(post(refreshUrl(account.userId())).header(HEADER, SECRET))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("login_required"));

        hiemdall.verify();
    }

    @Test
    void postRefresh_hiemdallReauthRequired_returns409AndDeletesSessions() throws Exception {
        UserOAuthAccount account = seedAccount("ya29", "1//r", NOW.plusSeconds(60), false);
        newSession(account.userId());
        newSession(account.userId());
        hiemdall.expect(requestTo(REFRESH_URL))
                .andRespond(
                        withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"error\": \"reauth_required\"}"));

        mvc.perform(post(refreshUrl(account.userId())).header(HEADER, SECRET))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("login_required"));

        assertThat(sessionsOf(account.userId())).isEmpty();
        assertThat(accounts.findById(account.oauthAccountId()).orElseThrow().loginRequired())
                .isTrue();
        hiemdall.verify();
    }

    // ─── 503 refresh_unavailable (+ Retry-After) ────────────────────────────

    @Test
    void getAccessToken_decryptFailure_returns503WithRetryAfter() throws Exception {
        // Valid-looking expiry (fast path) but ciphertext the key can't decrypt → 503, no refresh.
        UserOAuthAccount account = seedGarbageAccount(NOW.plus(Duration.ofHours(1)));

        mvc.perform(get(accessTokenUrl(account.userId())).header(HEADER, SECRET))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.error").value("refresh_unavailable"))
                .andExpect(
                        jsonPath("$.error_description")
                                .value("token refresh temporarily unavailable"));
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private UserOAuthAccount seedAccount(
            String accessToken, String refreshToken, Instant expiresAt, boolean loginRequired) {
        User user = users.save(User.newUser("u@example.com", "U", null));
        Ciphertext access = cipher.encrypt(accessToken);
        Ciphertext refresh = cipher.encrypt(refreshToken);
        return accounts.save(
                new UserOAuthAccount(
                        null,
                        user.userId(),
                        UserOAuthAccount.PROVIDER_GOOGLE,
                        "sub-" + UUID.randomUUID(),
                        "u@example.com",
                        "openid",
                        refresh.ciphertext(),
                        refresh.nonce(),
                        access.ciphertext(),
                        access.nonce(),
                        expiresAt,
                        NOW,
                        loginRequired,
                        null,
                        null));
    }

    private UserOAuthAccount seedGarbageAccount(Instant expiresAt) {
        User user = users.save(User.newUser("u@example.com", "U", null));
        byte[] garbage = {1, 2, 3, 4};
        byte[] nonce = new byte[12];
        return accounts.save(
                new UserOAuthAccount(
                        null,
                        user.userId(),
                        UserOAuthAccount.PROVIDER_GOOGLE,
                        "sub-" + UUID.randomUUID(),
                        "u@example.com",
                        "openid",
                        garbage,
                        nonce,
                        garbage,
                        nonce,
                        expiresAt,
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

    private static String accessTokenUrl(UUID userId) {
        return "/internal/users/" + userId + "/oauth/google/access-token";
    }

    private static String refreshUrl(UUID userId) {
        return "/internal/users/" + userId + "/oauth/google/refresh";
    }
}
