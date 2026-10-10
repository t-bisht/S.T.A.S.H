package org.tb.stash.user.hiemdall.service;

import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.tb.stash.user.config.HiemdallProperties;
import org.tb.stash.user.hiemdall.domain.HiemdallExchangeResponse;
import org.tb.stash.user.hiemdall.exception.HandoffExpiredException;
import org.tb.stash.user.hiemdall.exception.HiemdallUnavailableException;

/**
 * Thin HTTP wrapper over Hiemdall's {@code POST /internal/auth/exchange}.
 *
 * <p>Translates HTTP outcomes into typed exceptions: 404 → {@link HandoffExpiredException}; 5xx or
 * network error → {@link HiemdallUnavailableException}. 2xx returns the parsed body.
 *
 * <p>Must not run inside a DB transaction (parent spec §12.6): a network call holding a DB
 * connection open is wasteful.
 */
@Component
public class HiemdallExchangeClient {

    private static final Logger log = LoggerFactory.getLogger(HiemdallExchangeClient.class);
    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";

    private final RestClient http;
    private final HiemdallProperties props;
    private final URI exchangeUri;

    public HiemdallExchangeClient(RestClient http, HiemdallProperties props) {
        this.http = http;
        this.props = props;
        this.exchangeUri = props.baseUrl().resolve(props.exchangePath());
    }

    /** Redeems {@code handoff}; one-shot — a second call with the same code 404s. */
    public HiemdallExchangeResponse exchange(String handoff) {
        try {
            return http.post()
                    .uri(exchangeUri)
                    .header(INTERNAL_AUTH_HEADER, props.internalToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("app", props.appId(), "handoff", handoff))
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::is4xxClientError,
                            (req, res) -> {
                                if (res.getStatusCode().value() == 404) {
                                    throw new HandoffExpiredException(
                                            "Hiemdall rejected handoff (404 — expired, replayed, or wrong app)");
                                }
                                throw new HiemdallUnavailableException(
                                        "Hiemdall 4xx on exchange: " + res.getStatusCode(), null);
                            })
                    .onStatus(
                            HttpStatusCode::is5xxServerError,
                            (req, res) -> {
                                throw new HiemdallUnavailableException(
                                        "Hiemdall 5xx on exchange: " + res.getStatusCode(), null);
                            })
                    .body(HiemdallExchangeResponse.class);
        } catch (ResourceAccessException e) {
            log.warn("hiemdall.exchange.network-error uri={}", exchangeUri);
            throw new HiemdallUnavailableException("Hiemdall unreachable", e);
        }
    }
}
