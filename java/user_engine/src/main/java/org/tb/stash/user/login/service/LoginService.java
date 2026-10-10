package org.tb.stash.user.login.service;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.tb.stash.user.config.SessionProperties;
import org.tb.stash.user.crypto.Ciphertext;
import org.tb.stash.user.crypto.TokenCipher;
import org.tb.stash.user.core.domain.Session;
import org.tb.stash.user.core.domain.User;
import org.tb.stash.user.core.domain.UserOAuthAccount;
import org.tb.stash.user.hiemdall.service.HiemdallExchangeClient;
import org.tb.stash.user.hiemdall.domain.HiemdallExchangeResponse;
import org.tb.stash.user.hiemdall.domain.IdentityClaims;
import org.tb.stash.user.hiemdall.domain.OAuthTokenBundle;
import org.tb.stash.user.login.crud.IdentityUpsertService;
import org.tb.stash.user.login.domain.LoginResponse;
import org.tb.stash.user.login.domain.LoginResult;
import org.tb.stash.user.login.domain.UpsertResult;
import org.tb.stash.user.core.crud.SessionRepository;
import org.tb.stash.user.session.service.SessionHandleGenerator;

/**
 * Orchestrates login and logout.
 *
 * <p>Transaction topology per parent spec §12.6:
 *
 * <ul>
 *   <li>{@link HiemdallExchangeClient} HTTP call runs <em>outside</em> any DB transaction (never
 *       hold a connection open across the wire).
 *   <li>{@link IdentityUpsertService#upsert} runs its own transaction.
 *   <li>Session INSERT then happens outside that transaction. If it fails the user row survives;
 *       the next login mints a session cleanly.
 * </ul>
 *
 * <p>Both the refresh token and the access token are encrypted with {@link TokenCipher} before
 * reaching the DB. The access-token refresh client (that would call Google's token endpoint when
 * the stored access_token expires) is a deferred feature.
 */
@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    private final HiemdallExchangeClient hiemdall;
    private final TokenCipher cipher;
    private final IdentityUpsertService upsert;
    private final SessionHandleGenerator handleGenerator;
    private final SessionProperties sessionProps;
    private final SessionRepository sessions;

    public LoginService(
            HiemdallExchangeClient hiemdall,
            TokenCipher cipher,
            IdentityUpsertService upsert,
            SessionHandleGenerator handleGenerator,
            SessionProperties sessionProps,
            SessionRepository sessions) {
        this.hiemdall = hiemdall;
        this.cipher = cipher;
        this.upsert = upsert;
        this.handleGenerator = handleGenerator;
        this.sessionProps = sessionProps;
        this.sessions = sessions;
    }

    /** Full login flow for {@code POST /auth/session}. */
    public LoginResult login(String handoff) {
        log.debug("hiemdall.exchange handoff={}", handoff);
        HiemdallExchangeResponse exchange = hiemdall.exchange(handoff);
        OAuthTokenBundle tokens = exchange.tokens();
        IdentityClaims identity = exchange.identity();
        validate(tokens, identity);

        Ciphertext encRefresh = cipher.encrypt(tokens.refreshToken());
        Ciphertext encAccess = cipher.encrypt(tokens.accessToken());
        Instant accessTokenExpiresAt = Instant.now().plusSeconds(tokens.expiresIn());

        UpsertResult result =
                upsert.upsert(
                        identity, tokens.scope(), encRefresh, encAccess, accessTokenExpiresAt);

        String handle = mintSession(result.user());
        log.info(
                "session.minted userId={} provider={} isNewUser={} ttl={}",
                result.user().userId(),
                UserOAuthAccount.PROVIDER_GOOGLE,
                result.isNewUser(),
                sessionProps.ttl());

        LoginResponse body =
                new LoginResponse(
                        result.user().userId(),
                        result.user().primaryEmail(),
                        result.user().displayName(),
                        result.user().pictureUrl(),
                        result.isNewUser());
        return new LoginResult(handle, body);
    }

    /** Deletes the session row; idempotent. */
    public void logout(String sessionHandle) {
        if (sessionHandle == null || sessionHandle.isBlank()) {
            return;
        }
        sessions.deleteById(sessionHandle);
        log.info("session.destroyed");
    }

    private String mintSession(User user) {
        String handle = handleGenerator.generate();
        Instant now = Instant.now();
        sessions.save(new Session(handle, user.userId(), now, now, now.plus(sessionProps.ttl())));
        return handle;
    }

    private static void validate(OAuthTokenBundle tokens, IdentityClaims identity) {
        if (identity == null || identity.sub() == null || identity.sub().isBlank()) {
            throw new IllegalStateException("Hiemdall exchange returned no identity.sub");
        }
        if (tokens == null) {
            throw new IllegalStateException("Hiemdall exchange returned no tokens block");
        }
        if (tokens.refreshToken() == null || tokens.refreshToken().isBlank()) {
            throw new IllegalStateException(
                    "Hiemdall exchange returned no refresh_token for sub=" + identity.sub());
        }
        if (tokens.accessToken() == null || tokens.accessToken().isBlank()) {
            throw new IllegalStateException(
                    "Hiemdall exchange returned no access_token for sub=" + identity.sub());
        }
        if (tokens.expiresIn() <= 0) {
            throw new IllegalStateException(
                    "Hiemdall exchange returned non-positive expires_in=" + tokens.expiresIn());
        }
    }
}
