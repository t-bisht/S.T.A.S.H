package org.tb.stash.user.hiemdall.service;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.client.RestClientException;
import org.tb.stash.user.config.HiemdallProperties;
import org.tb.stash.user.hiemdall.domain.HiemdallErrorResponse;
import org.tb.stash.user.hiemdall.domain.HiemdallRefreshResponse;
import org.tb.stash.user.hiemdall.domain.RefreshOutcome;

/**
 * HTTP wrapper over Hiemdall's {@code POST /internal/auth/google/refresh}, which swaps a Google
 * refresh token for a new access token.
 *
 * <p>Never throws for an HTTP or network outcome: every response is classified into a {@link
 * RefreshOutcome} so the caller decides between logout, retry and alerting.
 *
 * <table>
 *   <caption>Classification</caption>
 *   <tr><th>Hiemdall response</th><th>Outcome</th></tr>
 *   <tr><td>200 with valid body</td><td>{@code SUCCEEDED}</td></tr>
 *   <tr><td>200 with invalid body</td><td>{@code TRANSIENT_FAILURE}
 *       ({@code invalid_upstream_response})</td></tr>
 *   <tr><td>401 {@code reauth_required}</td><td>{@code LOGIN_REQUIRED}</td></tr>
 *   <tr><td>401 anything else</td><td>{@code CONFIG_ERROR} ({@code hiemdall_auth})</td></tr>
 *   <tr><td>other 4xx</td><td>{@code CONFIG_ERROR} (Hiemdall's {@code error})</td></tr>
 *   <tr><td>5xx / other</td><td>{@code TRANSIENT_FAILURE}</td></tr>
 *   <tr><td>network error / unparseable body</td><td>{@code TRANSIENT_FAILURE}
 *       ({@code hiemdall_unreachable})</td></tr>
 * </table>
 *
 * <p><strong>Both "user must re-auth" and "wrong service secret" are HTTP 401.</strong> Only an
 * explicit {@code reauth_required} body yields {@code LOGIN_REQUIRED}; anything ambiguous is
 * treated as a config error, because misclassifying it would log out every user.
 *
 * <p>Must not run inside a DB transaction (a network call must never hold a connection open). Logs
 * carry status and error code only — never tokens or response bodies.
 */
@Component
public class HiemdallRefreshClient {

    private static final Logger log = LoggerFactory.getLogger(HiemdallRefreshClient.class);
    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";

    /** Upper bound on a rotated refresh token; Google's are ~100 chars. Guards the DB column. */
    static final int MAX_REFRESH_TOKEN_LENGTH = 2048;

    private final RestClient http;
    private final HiemdallProperties props;
    private final URI refreshUri;

    public HiemdallRefreshClient(RestClient http, HiemdallProperties props) {
        this.http = http;
        this.props = props;
        this.refreshUri = props.baseUrl().resolve(props.refreshPath());
    }

    /**
     * Asks Hiemdall for a new access token.
     *
     * @param refreshToken plaintext Google refresh token; must not be blank
     * @return classified outcome; never {@code null}
     */
    public RefreshOutcome refresh(String refreshToken) {
        try {
            return http.post()
                    .uri(refreshUri)
                    .header(INTERNAL_AUTH_HEADER, props.internalToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("app", props.appId(), "refresh_token", refreshToken))
                    .exchange((request, response) -> classify(response));
        } catch (RestClientException e) {
            // ResourceAccessException (connect / read timeout) or a body that failed to parse.
            log.warn(
                    "token_refresh.hiemdall.unreachable uri={} cause={}",
                    refreshUri,
                    e.getClass().getSimpleName());
            return RefreshOutcome.transientFailure(RefreshOutcome.HIEMDALL_UNREACHABLE);
        }
    }

    private RefreshOutcome classify(ConvertibleClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        if (status.is2xxSuccessful()) {
            return validate(response.bodyTo(HiemdallRefreshResponse.class));
        }
        if (status.value() == HttpStatus.UNAUTHORIZED.value()) {
            return classifyUnauthorized(readError(response));
        }
        String code = errorCodeOr(readError(response), "http_" + status.value());
        if (status.is4xxClientError()) {
            log.error("token_refresh.hiemdall.rejected status={} error={}", status.value(), code);
            return RefreshOutcome.configError(code);
        }
        log.warn("token_refresh.hiemdall.upstream_error status={} error={}", status.value(), code);
        return RefreshOutcome.transientFailure(code);
    }

    private static RefreshOutcome classifyUnauthorized(HiemdallErrorResponse error) {
        if (error != null && error.isReauthRequired()) {
            return RefreshOutcome.loginRequired();
        }
        log.error(
                "token_refresh.hiemdall.internal_auth_rejected — check HIEMDALL_INTERNAL_TOKEN"
                        + " matches Hiemdall");
        return RefreshOutcome.configError(RefreshOutcome.HIEMDALL_AUTH);
    }

    private static RefreshOutcome validate(HiemdallRefreshResponse body) {
        if (body == null
                || isBlank(body.accessToken())
                || body.expiresIn() == null
                || body.expiresIn() <= 0
                || !isValidRotatedRefreshToken(body.refreshToken())) {
            log.error("token_refresh.hiemdall.invalid_response status=200");
            return RefreshOutcome.transientFailure(RefreshOutcome.INVALID_UPSTREAM_RESPONSE);
        }
        return RefreshOutcome.succeeded(body);
    }

    /** {@code null} means "not rotated" (valid); a present value must be non-blank and bounded. */
    private static boolean isValidRotatedRefreshToken(String refreshToken) {
        return refreshToken == null
                || (!refreshToken.isBlank() && refreshToken.length() <= MAX_REFRESH_TOKEN_LENGTH);
    }

    /**
     * Best-effort parse of an error body; {@code null} when absent or not JSON. Never throws, so an
     * odd error body can't turn a known status into {@code hiemdall_unreachable}.
     */
    private static HiemdallErrorResponse readError(ConvertibleClientHttpResponse response) {
        try {
            return response.bodyTo(HiemdallErrorResponse.class);
        } catch (RestClientException e) {
            return null;
        }
    }

    private static String errorCodeOr(HiemdallErrorResponse error, String fallback) {
        return error == null || isBlank(error.error()) ? fallback : error.error();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
