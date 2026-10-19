package org.tb.stash.user.tokenrefresh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tb.stash.user.core.domain.UserOAuthAccount;
import org.tb.stash.user.crypto.Ciphertext;
import org.tb.stash.user.crypto.TokenCipherException;
import org.tb.stash.user.hiemdall.domain.HiemdallRefreshResponse;
import org.tb.stash.user.hiemdall.domain.RefreshOutcome;
import org.tb.stash.user.tokenrefresh.domain.IssuedAccessToken;
import org.tb.stash.user.tokenrefresh.domain.RefreshFailureCode;
import org.tb.stash.user.tokenrefresh.exception.LoginRequiredException;
import org.tb.stash.user.tokenrefresh.exception.OAuthAccountNotFoundException;
import org.tb.stash.user.tokenrefresh.exception.RefreshUnavailableException;

class TokenRefreshServiceTest extends TokenRefreshTestSupport {

    private TokenRefreshService service;

    @BeforeEach
    void setUp() {
        service = new TokenRefreshService(accounts, exchanger, locks, metrics, PROPS, CLOCK);
    }

    @Test
    void refreshesEvenWhenTokenFarFromExpiry() {
        UserOAuthAccount account = account(NOW.plus(Duration.ofMinutes(50)), null);
        givenUserAccount(account);
        when(hiemdall.refresh(REFRESH_TOKEN)).thenReturn(success("ya29.new", null));

        IssuedAccessToken token = service.refreshForUser(USER_ID);

        assertThat(token.accessToken()).isEqualTo("ya29.new");
        assertThat(token.expiresAt()).isEqualTo(NOW.plusSeconds(3599));
        verify(persistence)
                .storeRefreshed(
                        account.oauthAccountId(),
                        NEW_ACCESS_BOX,
                        NOW.plusSeconds(3599),
                        "openid",
                        null,
                        NOW);
        assertThat(counter("on_demand", "refreshed")).isEqualTo(1.0);
    }

    @Test
    void rotatedRefreshToken_isEncryptedAndStored() {
        UserOAuthAccount account = account(NOW.plusSeconds(60), null);
        givenUserAccount(account);
        Ciphertext rotatedBox = new Ciphertext(new byte[] {9}, new byte[] {9});
        when(cipher.encrypt("1//rotated")).thenReturn(rotatedBox);
        when(hiemdall.refresh(REFRESH_TOKEN)).thenReturn(success("ya29.new", "1//rotated"));

        service.refreshForUser(USER_ID);

        verify(persistence)
                .storeRefreshed(
                        eq(account.oauthAccountId()),
                        eq(NEW_ACCESS_BOX),
                        any(),
                        any(),
                        eq(rotatedBox),
                        any());
    }

    @Test
    void recentRefresh_isCoalescedWithoutCallingHiemdall() {
        UserOAuthAccount account = account(NOW.plus(Duration.ofMinutes(59)), NOW.minusSeconds(5));
        givenUserAccount(account);

        IssuedAccessToken token = service.refreshForUser(USER_ID);

        assertThat(token.accessToken()).isEqualTo(STORED_ACCESS_TOKEN);
        assertThat(token.expiresAt()).isEqualTo(account.accessTokenExpiresAt());
        verifyNoInteractions(hiemdall);
        assertThat(counter("on_demand", "skipped_fresh")).isEqualTo(1.0);
    }

    @Test
    void refreshOlderThanCoalesceWindow_callsHiemdall() {
        givenUserAccount(account(NOW.plus(Duration.ofMinutes(59)), NOW.minusSeconds(31)));
        when(hiemdall.refresh(REFRESH_TOKEN)).thenReturn(success("ya29.new", null));

        assertThat(service.refreshForUser(USER_ID).accessToken()).isEqualTo("ya29.new");
    }

    @Test
    void noGoogleAccount_throwsNotFound() {
        when(accounts.findByUserIdAndProvider(USER_ID, UserOAuthAccount.PROVIDER_GOOGLE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refreshForUser(USER_ID))
                .isInstanceOf(OAuthAccountNotFoundException.class);
        verifyNoInteractions(hiemdall, locks);
    }

    @Test
    void alreadyFlagged_throwsLoginRequiredWithoutLocking() {
        when(accounts.findByUserIdAndProvider(USER_ID, UserOAuthAccount.PROVIDER_GOOGLE))
                .thenReturn(Optional.of(flagged(account(NOW, null))));

        assertThatThrownBy(() -> service.refreshForUser(USER_ID))
                .isInstanceOf(LoginRequiredException.class);
        verifyNoInteractions(hiemdall, locks);
    }

    @Test
    void flaggedWhileWaitingForLock_throwsLoginRequiredAndUnlocks() {
        UserOAuthAccount account = account(NOW, null);
        when(accounts.findByUserIdAndProvider(USER_ID, UserOAuthAccount.PROVIDER_GOOGLE))
                .thenReturn(Optional.of(account));
        when(accounts.findById(account.oauthAccountId())).thenReturn(Optional.of(flagged(account)));

        assertThatThrownBy(() -> service.refreshForUser(USER_ID))
                .isInstanceOf(LoginRequiredException.class);
        verifyNoInteractions(hiemdall);
        verify(locks).unlock(account.oauthAccountId());
    }

    @Test
    void lockTimeout_throwsUnavailable() {
        UserOAuthAccount account = account(NOW, null);
        givenUserAccount(account);
        when(locks.tryLock(account.oauthAccountId(), PROPS.lockWait())).thenReturn(false);

        assertThatThrownBy(() -> service.refreshForUser(USER_ID))
                .isInstanceOf(RefreshUnavailableException.class)
                .extracting(e -> ((RefreshUnavailableException) e).reason())
                .isEqualTo("lock_timeout");
        verifyNoInteractions(hiemdall);
        verify(locks, never()).unlock(any());
    }

    @Test
    void reauthRequired_forcesLogoutAndThrowsLoginRequired() {
        UserOAuthAccount account = account(NOW, null);
        givenUserAccount(account);
        when(hiemdall.refresh(REFRESH_TOKEN)).thenReturn(RefreshOutcome.loginRequired());

        assertThatThrownBy(() -> service.refreshForUser(USER_ID))
                .isInstanceOf(LoginRequiredException.class);
        verify(persistence).forceLogout(account.oauthAccountId(), USER_ID);
        verify(locks).unlock(account.oauthAccountId());
        assertThat(counter("on_demand", "logged_out")).isEqualTo(1.0);
    }

    @Test
    void transientFailure_recordsUpstreamUnavailableAndThrows() {
        UserOAuthAccount account = account(NOW, null);
        givenUserAccount(account);
        when(hiemdall.refresh(REFRESH_TOKEN))
                .thenReturn(RefreshOutcome.transientFailure("upstream_unavailable"));

        assertThatThrownBy(() -> service.refreshForUser(USER_ID))
                .isInstanceOf(RefreshUnavailableException.class);
        verify(persistence)
                .recordFailure(account.oauthAccountId(), RefreshFailureCode.UPSTREAM_UNAVAILABLE);
        verify(locks).unlock(account.oauthAccountId());
    }

    @Test
    void invalidUpstreamResponse_recordsThatCode() {
        UserOAuthAccount account = account(NOW, null);
        givenUserAccount(account);
        when(hiemdall.refresh(REFRESH_TOKEN))
                .thenReturn(
                        RefreshOutcome.transientFailure(RefreshOutcome.INVALID_UPSTREAM_RESPONSE));

        assertThatThrownBy(() -> service.refreshForUser(USER_ID))
                .isInstanceOf(RefreshUnavailableException.class);
        verify(persistence)
                .recordFailure(
                        account.oauthAccountId(), RefreshFailureCode.INVALID_UPSTREAM_RESPONSE);
    }

    @Test
    void configError_recordsConfigErrorAndThrows() {
        UserOAuthAccount account = account(NOW, null);
        givenUserAccount(account);
        when(hiemdall.refresh(REFRESH_TOKEN))
                .thenReturn(RefreshOutcome.configError(RefreshOutcome.HIEMDALL_AUTH));

        assertThatThrownBy(() -> service.refreshForUser(USER_ID))
                .isInstanceOf(RefreshUnavailableException.class);
        verify(persistence)
                .recordFailure(account.oauthAccountId(), RefreshFailureCode.CONFIG_ERROR);
        verify(locks).unlock(account.oauthAccountId());
    }

    @Test
    void decryptFailure_recordsDecryptFailedAndThrows() {
        UserOAuthAccount account = account(NOW, null);
        givenUserAccount(account);
        when(cipher.decrypt(any(byte[].class), any(byte[].class)))
                .thenThrow(new TokenCipherException("bad", null));

        assertThatThrownBy(() -> service.refreshForUser(USER_ID))
                .isInstanceOf(RefreshUnavailableException.class)
                .extracting(e -> ((RefreshUnavailableException) e).reason())
                .isEqualTo("decrypt_failed");
        verify(persistence)
                .recordFailure(account.oauthAccountId(), RefreshFailureCode.DECRYPT_FAILED);
        verifyNoInteractions(hiemdall);
        verify(locks).unlock(account.oauthAccountId());
    }

    @Test
    void supersededByConcurrentLogin_returnsStoredToken() {
        UserOAuthAccount account = account(NOW.plusSeconds(60), null);
        UserOAuthAccount afterLogin = account(NOW.plus(Duration.ofMinutes(60)), null);
        when(accounts.findByUserIdAndProvider(USER_ID, UserOAuthAccount.PROVIDER_GOOGLE))
                .thenReturn(Optional.of(account));
        when(accounts.findById(account.oauthAccountId()))
                .thenReturn(Optional.of(account))
                .thenReturn(Optional.of(afterLogin));
        when(hiemdall.refresh(REFRESH_TOKEN)).thenReturn(success("ya29.new", null));
        when(persistence.storeRefreshed(any(), any(), any(), any(), any(), any()))
                .thenReturn(false);

        IssuedAccessToken token = service.refreshForUser(USER_ID);

        assertThat(token.accessToken()).isEqualTo(STORED_ACCESS_TOKEN);
        assertThat(token.expiresAt()).isEqualTo(afterLogin.accessTokenExpiresAt());
    }

    @Test
    void unexpectedDbError_propagatesAndUnlocks() {
        UserOAuthAccount account = account(NOW, null);
        givenUserAccount(account);
        when(hiemdall.refresh(REFRESH_TOKEN)).thenReturn(success("ya29.new", null));
        when(persistence.storeRefreshed(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.refreshForUser(USER_ID))
                .isInstanceOf(IllegalStateException.class);
        verify(locks).unlock(account.oauthAccountId());
    }

    // ─── getAccessToken (spec 02) ───────────────────────────────────────────

    @Test
    void getAccessToken_beyondMinValidity_returnsStoredWithoutClientOrLock() {
        givenUserAccount(account(NOW.plus(Duration.ofMinutes(50)), null));

        IssuedAccessToken token = service.getAccessToken(USER_ID);

        assertThat(token.accessToken()).isEqualTo(STORED_ACCESS_TOKEN);
        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(50)));
        verifyNoInteractions(hiemdall, locks, persistence);
        assertThat(counter("access_token_fetch", "refreshed")).isZero();
        assertThat(counter("access_token_fetch", "skipped_fresh")).isZero();
    }

    @Test
    void getAccessToken_withinMinValidity_refreshesInline() {
        givenUserAccount(account(NOW.plusSeconds(60), null));
        when(hiemdall.refresh(REFRESH_TOKEN)).thenReturn(success("ya29.new", null));

        IssuedAccessToken token = service.getAccessToken(USER_ID);

        assertThat(token.accessToken()).isEqualTo("ya29.new");
        assertThat(token.expiresAt()).isEqualTo(NOW.plusSeconds(3599));
        assertThat(counter("access_token_fetch", "refreshed")).isEqualTo(1.0);
    }

    @Test
    void getAccessToken_expiredToken_refreshesInline() {
        givenUserAccount(account(NOW.minusSeconds(1), null));
        when(hiemdall.refresh(REFRESH_TOKEN)).thenReturn(success("ya29.new", null));

        assertThat(service.getAccessToken(USER_ID).accessToken()).isEqualTo("ya29.new");
    }

    @Test
    void getAccessToken_loginRequired_throwsWithoutDecryptOrLock() {
        when(accounts.findByUserIdAndProvider(USER_ID, UserOAuthAccount.PROVIDER_GOOGLE))
                .thenReturn(Optional.of(flagged(account(NOW.plus(Duration.ofMinutes(50)), null))));

        assertThatThrownBy(() -> service.getAccessToken(USER_ID))
                .isInstanceOf(LoginRequiredException.class);
        verifyNoInteractions(hiemdall, locks, persistence, cipher);
    }

    @Test
    void getAccessToken_noGoogleAccount_throwsNotFound() {
        when(accounts.findByUserIdAndProvider(USER_ID, UserOAuthAccount.PROVIDER_GOOGLE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAccessToken(USER_ID))
                .isInstanceOf(OAuthAccountNotFoundException.class);
        verifyNoInteractions(hiemdall, locks);
    }

    @Test
    void getAccessToken_fastPathDecryptFailure_throwsUnavailableWithoutRefresh() {
        givenUserAccount(account(NOW.plus(Duration.ofMinutes(50)), null));
        when(cipher.decrypt(any(byte[].class), any(byte[].class)))
                .thenThrow(new TokenCipherException("bad", null));

        assertThatThrownBy(() -> service.getAccessToken(USER_ID))
                .isInstanceOf(RefreshUnavailableException.class)
                .extracting(e -> ((RefreshUnavailableException) e).reason())
                .isEqualTo("decrypt_failed");
        verifyNoInteractions(hiemdall, locks);
    }

    @Test
    void getAccessToken_inlineRefreshStillWithinMinValidity_throwsUnavailable() {
        givenUserAccount(account(NOW.plusSeconds(30), null));
        // Hiemdall returns a token that itself dies within min-validity (PT2M).
        when(hiemdall.refresh(REFRESH_TOKEN))
                .thenReturn(
                        RefreshOutcome.succeeded(
                                new HiemdallRefreshResponse(
                                        "ya29.short", null, 60L, "openid", "Bearer")));

        assertThatThrownBy(() -> service.getAccessToken(USER_ID))
                .isInstanceOf(RefreshUnavailableException.class)
                .extracting(e -> ((RefreshUnavailableException) e).reason())
                .isEqualTo("insufficient_validity");
    }

    private void givenUserAccount(UserOAuthAccount account) {
        when(accounts.findByUserIdAndProvider(USER_ID, UserOAuthAccount.PROVIDER_GOOGLE))
                .thenReturn(Optional.of(account));
        when(accounts.findById(account.oauthAccountId())).thenReturn(Optional.of(account));
    }
}
