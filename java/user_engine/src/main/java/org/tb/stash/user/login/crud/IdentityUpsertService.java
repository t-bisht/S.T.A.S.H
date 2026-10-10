package org.tb.stash.user.login.crud;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.stash.user.crypto.Ciphertext;
import org.tb.stash.user.core.domain.User;
import org.tb.stash.user.core.domain.UserOAuthAccount;
import org.tb.stash.user.hiemdall.domain.IdentityClaims;
import org.tb.stash.user.core.crud.UserOAuthAccountRepository;
import org.tb.stash.user.core.crud.UserRepository;
import org.tb.stash.user.login.domain.UpsertResult;
import org.tb.stash.user.login.service.LoginService;

/**
 * Transactional upsert of {@code users} + {@code user_oauth_accounts} given verified identity
 * claims and the pre-encrypted refresh + access tokens.
 *
 * <p>Lives in its own bean so that {@link LoginService} can call it through the Spring proxy — a
 * {@code @Transactional} method on the same bean would be bypassed by self-invocation.
 *
 * <p>Both token halves are rewritten on every login (first-time and re-login) with fresh
 * ciphertexts and fresh nonces. {@code access_token_expires_at} is a wall-clock Instant computed by
 * the caller as {@code now() + tokens.expires_in}.
 */
@Service
public class IdentityUpsertService {

    private final UserRepository users;
    private final UserOAuthAccountRepository oauthAccounts;

    public IdentityUpsertService(UserRepository users, UserOAuthAccountRepository oauthAccounts) {
        this.users = users;
        this.oauthAccounts = oauthAccounts;
    }

    /**
     * Create or refresh the identity rows for a login.
     *
     * <p>Both branches run inside a single transaction; a failure in either INSERT/UPDATE rolls the
     * whole upsert back.
     */
    @Transactional
    public UpsertResult upsert(
            IdentityClaims identity,
            String scope,
            Ciphertext refreshToken,
            Ciphertext accessToken,
            Instant accessTokenExpiresAt) {
        Instant now = Instant.now();

        return oauthAccounts
                .findByProviderAndProviderSub(UserOAuthAccount.PROVIDER_GOOGLE, identity.sub())
                .map(
                        existing ->
                                updateExisting(
                                        existing,
                                        identity,
                                        scope,
                                        refreshToken,
                                        accessToken,
                                        accessTokenExpiresAt,
                                        now))
                .orElseGet(
                        () ->
                                insertNew(
                                        identity,
                                        scope,
                                        refreshToken,
                                        accessToken,
                                        accessTokenExpiresAt));
    }

    private UpsertResult updateExisting(
            UserOAuthAccount existing,
            IdentityClaims identity,
            String scope,
            Ciphertext refreshToken,
            Ciphertext accessToken,
            Instant accessTokenExpiresAt,
            Instant now) {
        User existingUser =
                users.findById(existing.userId())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "oauth account references missing user_id="
                                                        + existing.userId()));
        User updated =
                new User(
                        existingUser.userId(),
                        identity.email(),
                        identity.name(),
                        identity.picture(),
                        existingUser.status(),
                        existingUser.createdAt(),
                        now);
        users.save(updated);

        oauthAccounts.save(
                new UserOAuthAccount(
                        existing.oauthAccountId(),
                        existing.userId(),
                        existing.provider(),
                        existing.providerSub(),
                        identity.email(),
                        scope,
                        refreshToken.ciphertext(),
                        refreshToken.nonce(),
                        accessToken.ciphertext(),
                        accessToken.nonce(),
                        accessTokenExpiresAt,
                        existing.linkedAt()));
        return new UpsertResult(updated, false);
    }

    private UpsertResult insertNew(
            IdentityClaims identity,
            String scope,
            Ciphertext refreshToken,
            Ciphertext accessToken,
            Instant accessTokenExpiresAt) {
        User saved =
                users.save(User.newUser(identity.email(), identity.name(), identity.picture()));
        oauthAccounts.save(
                new UserOAuthAccount(
                        null,
                        saved.userId(),
                        UserOAuthAccount.PROVIDER_GOOGLE,
                        identity.sub(),
                        identity.email(),
                        scope,
                        refreshToken.ciphertext(),
                        refreshToken.nonce(),
                        accessToken.ciphertext(),
                        accessToken.nonce(),
                        accessTokenExpiresAt,
                        Instant.now()));
        return new UpsertResult(saved, true);
    }
}
