package org.tb.stash.user.hiemdall.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import org.tb.stash.user.config.HiemdallProperties;
import org.tb.stash.user.hiemdall.domain.RefreshOutcome;

class HiemdallRefreshClientTest {

    private static final String REFRESH_URL = "http://hiemdall.test/internal/auth/google/refresh";
    private static final String SECRET = "svc-secret";
    private static final String REFRESH_TOKEN = "1//refresh-token";

    private MockRestServiceServer server;
    private HiemdallRefreshClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        HiemdallProperties props =
                new HiemdallProperties(
                        URI.create("http://hiemdall.test"),
                        "stash",
                        SECRET,
                        "/internal/auth/exchange",
                        "/internal/auth/google/refresh");
        client = new HiemdallRefreshClient(builder.build(), props);
    }

    @Test
    void refresh_sendsInternalAuthHeaderAppAndRefreshToken() {
        server.expect(requestTo(REFRESH_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Auth", SECRET))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(
                        content()
                                .json(
                                        """
                                        {"app": "stash", "refresh_token": "1//refresh-token"}
                                        """,
                                        true))
                .andRespond(okJson(validBody()));

        client.refresh(REFRESH_TOKEN);

        server.verify();
    }

    @Test
    void refresh_200WithValidBody_succeeds() {
        respond(okJson(validBody()));

        RefreshOutcome outcome = client.refresh(REFRESH_TOKEN);

        assertThat(outcome.status()).isEqualTo(RefreshOutcome.Status.SUCCEEDED);
        assertThat(outcome.body().accessToken()).isEqualTo("ya29.new");
        assertThat(outcome.body().expiresIn()).isEqualTo(3599L);
        assertThat(outcome.body().refreshToken()).isNull();
    }

    @Test
    void refresh_200WithRotatedRefreshToken_succeeds() {
        respond(
                okJson(
                        """
                        {"access_token": "ya29.new", "expires_in": 3599, "refresh_token": "1//rotated"}
                        """));

        RefreshOutcome outcome = client.refresh(REFRESH_TOKEN);

        assertThat(outcome.body().refreshToken()).isEqualTo("1//rotated");
    }

    @Test
    void refresh_200WithBlankAccessToken_isInvalidUpstreamResponse() {
        respond(okJson("{\"access_token\": \" \", \"expires_in\": 3599}"));

        assertThat(client.refresh(REFRESH_TOKEN))
                .isEqualTo(
                        RefreshOutcome.transientFailure(RefreshOutcome.INVALID_UPSTREAM_RESPONSE));
    }

    @Test
    void refresh_200WithMissingExpiresIn_isInvalidUpstreamResponse() {
        respond(okJson("{\"access_token\": \"ya29.new\"}"));

        assertThat(client.refresh(REFRESH_TOKEN))
                .isEqualTo(
                        RefreshOutcome.transientFailure(RefreshOutcome.INVALID_UPSTREAM_RESPONSE));
    }

    @Test
    void refresh_200WithNonPositiveExpiresIn_isInvalidUpstreamResponse() {
        respond(okJson("{\"access_token\": \"ya29.new\", \"expires_in\": 0}"));

        assertThat(client.refresh(REFRESH_TOKEN))
                .isEqualTo(
                        RefreshOutcome.transientFailure(RefreshOutcome.INVALID_UPSTREAM_RESPONSE));
    }

    @Test
    void refresh_200WithBlankRotatedRefreshToken_isInvalidUpstreamResponse() {
        respond(
                okJson(
                        "{\"access_token\": \"ya29.new\", \"expires_in\": 3599,"
                                + " \"refresh_token\": \"\"}"));

        assertThat(client.refresh(REFRESH_TOKEN))
                .isEqualTo(
                        RefreshOutcome.transientFailure(RefreshOutcome.INVALID_UPSTREAM_RESPONSE));
    }

    @Test
    void refresh_200WithOversizedRotatedRefreshToken_isInvalidUpstreamResponse() {
        String huge = "x".repeat(HiemdallRefreshClient.MAX_REFRESH_TOKEN_LENGTH + 1);
        respond(
                okJson(
                        "{\"access_token\": \"ya29.new\", \"expires_in\": 3599,"
                                + " \"refresh_token\": \""
                                + huge
                                + "\"}"));

        assertThat(client.refresh(REFRESH_TOKEN))
                .isEqualTo(
                        RefreshOutcome.transientFailure(RefreshOutcome.INVALID_UPSTREAM_RESPONSE));
    }

    @Test
    void refresh_401ReauthRequired_isLoginRequired() {
        respond(
                json(
                        HttpStatus.UNAUTHORIZED,
                        """
                        {"error": "reauth_required",
                         "error_description": "Google refresh token is expired or revoked"}
                        """));

        assertThat(client.refresh(REFRESH_TOKEN)).isEqualTo(RefreshOutcome.loginRequired());
    }

    @Test
    void refresh_401FromInternalAuthFilter_isConfigErrorNotLogout() {
        respond(json(HttpStatus.UNAUTHORIZED, "{\"error\": \"X-Internal-Auth does not match\"}"));

        assertThat(client.refresh(REFRESH_TOKEN))
                .isEqualTo(RefreshOutcome.configError(RefreshOutcome.HIEMDALL_AUTH));
    }

    @Test
    void refresh_401WithUnparseableBody_isConfigErrorNotLogout() {
        respond(withStatus(HttpStatus.UNAUTHORIZED).body("nope"));

        assertThat(client.refresh(REFRESH_TOKEN))
                .isEqualTo(RefreshOutcome.configError(RefreshOutcome.HIEMDALL_AUTH));
    }

    @Test
    void refresh_400UnknownApp_isConfigErrorWithCode() {
        respond(json(HttpStatus.BAD_REQUEST, "{\"error\": \"unknown_app\"}"));

        assertThat(client.refresh(REFRESH_TOKEN))
                .isEqualTo(RefreshOutcome.configError("unknown_app"));
    }

    @Test
    void refresh_400InvalidRequest_isConfigErrorWithCode() {
        respond(json(HttpStatus.BAD_REQUEST, "{\"error\": \"invalid_request\"}"));

        assertThat(client.refresh(REFRESH_TOKEN))
                .isEqualTo(RefreshOutcome.configError("invalid_request"));
    }

    @Test
    void refresh_502UpstreamUnavailable_isTransientWithCode() {
        respond(json(HttpStatus.BAD_GATEWAY, "{\"error\": \"upstream_unavailable\"}"));

        assertThat(client.refresh(REFRESH_TOKEN))
                .isEqualTo(RefreshOutcome.transientFailure("upstream_unavailable"));
    }

    @Test
    void refresh_500WithoutBody_isTransientWithStatus() {
        respond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(client.refresh(REFRESH_TOKEN))
                .isEqualTo(RefreshOutcome.transientFailure("http_500"));
    }

    @Test
    void refresh_networkTimeout_isTransientUnreachable() {
        respond(withException(new SocketTimeoutException("read timed out")));

        assertThat(client.refresh(REFRESH_TOKEN))
                .isEqualTo(RefreshOutcome.transientFailure(RefreshOutcome.HIEMDALL_UNREACHABLE));
    }

    @Test
    void refresh_networkError_isTransientUnreachable() {
        respond(withException(new IOException("connection refused")));

        assertThat(client.refresh(REFRESH_TOKEN))
                .isEqualTo(RefreshOutcome.transientFailure(RefreshOutcome.HIEMDALL_UNREACHABLE));
    }

    @Test
    void refresh_200WithUnparseableBody_isTransientUnreachable() {
        respond(withSuccess("not json", MediaType.APPLICATION_JSON));

        assertThat(client.refresh(REFRESH_TOKEN))
                .isEqualTo(RefreshOutcome.transientFailure(RefreshOutcome.HIEMDALL_UNREACHABLE));
    }

    private void respond(ResponseCreator response) {
        server.expect(requestTo(REFRESH_URL)).andRespond(response);
    }

    private static ResponseCreator okJson(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }

    private static ResponseCreator json(HttpStatus status, String body) {
        return withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static String validBody() {
        return """
               {"access_token": "ya29.new", "expires_in": 3599,
                "scope": "openid email", "token_type": "Bearer"}
               """;
    }
}
