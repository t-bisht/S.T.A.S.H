package org.tb.stash.user.tokenrefresh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tb.stash.user.core.domain.UserOAuthAccount;
import org.tb.stash.user.crypto.TokenCipherException;
import org.tb.stash.user.hiemdall.domain.RefreshOutcome;
import org.tb.stash.user.tokenrefresh.domain.RefreshFailureCode;

class TokenRefreshSweeperTest extends TokenRefreshTestSupport {

    private final Instant threshold = NOW.plus(PROPS.refreshWindow());

    private TokenRefreshSweeper sweeper;

    @BeforeEach
    void setUp() {
        sweeper = new TokenRefreshSweeper(accounts, exchanger, locks, metrics, PROPS, CLOCK);
    }

    @Test
    void refreshesEachDueRowAcrossKeysetPages() {
        UserOAuthAccount a = account(NOW.plusSeconds(60), null);
        UserOAuthAccount b = account(NOW.plusSeconds(120), null);
        UserOAuthAccount c = account(NOW.plusSeconds(180), null);
        when(accounts.findDueForRefresh(eq(threshold), eq(Instant.EPOCH), any(), eq(2)))
                .thenReturn(List.of(a, b));
        when(accounts.findDueForRefresh(threshold, b.accessTokenExpiresAt(), b.oauthAccountId(), 2))
                .thenReturn(List.of(c));
        when(hiemdall.refresh(REFRESH_TOKEN)).thenReturn(success("ya29.new", null));

        sweeper.sweep();

        verify(hiemdall, times(3)).refresh(REFRESH_TOKEN);
        verify(accounts, times(2)).findDueForRefresh(any(), any(), any(), anyInt());
        assertThat(counter("sweep", "refreshed")).isEqualTo(3.0);
    }

    @Test
    void busyAccount_isSkippedWithoutWaiting() {
        UserOAuthAccount a = account(NOW.plusSeconds(60), null);
        givenSinglePage(a);
        when(locks.tryLock(a.oauthAccountId(), Duration.ZERO)).thenReturn(false);

        sweeper.sweep();

        verifyNoInteractions(hiemdall);
        assertThat(counter("sweep", "skipped_busy")).isEqualTo(1.0);
    }

    @Test
    void reauthRequired_forcesLogoutAndContinues() {
        UserOAuthAccount dead = account(NOW.plusSeconds(60), null, "1//dead");
        UserOAuthAccount alive = account(NOW.plusSeconds(120), null);
        givenSinglePage(dead, alive);
        when(hiemdall.refresh("1//dead")).thenReturn(RefreshOutcome.loginRequired());
        when(hiemdall.refresh(REFRESH_TOKEN)).thenReturn(success("ya29.new", null));

        sweeper.sweep();

        verify(persistence).forceLogout(dead.oauthAccountId(), USER_ID);
        assertThat(counter("sweep", "logged_out")).isEqualTo(1.0);
        assertThat(counter("sweep", "refreshed")).isEqualTo(1.0);
    }

    @Test
    void configError_abortsRunAfterFirstRow() {
        UserOAuthAccount a = account(NOW.plusSeconds(60), null);
        UserOAuthAccount b = account(NOW.plusSeconds(120), null);
        givenSinglePage(a, b);
        when(hiemdall.refresh(REFRESH_TOKEN))
                .thenReturn(RefreshOutcome.configError(RefreshOutcome.HIEMDALL_AUTH));

        sweeper.sweep();

        verify(hiemdall, times(1)).refresh(REFRESH_TOKEN);
        verify(persistence).recordFailure(a.oauthAccountId(), RefreshFailureCode.CONFIG_ERROR);
        verify(persistence, never()).recordFailure(eq(b.oauthAccountId()), any());
        verify(locks).unlock(a.oauthAccountId());
    }

    @Test
    void exceptionOnOneRow_isCountedAndRunContinues() {
        UserOAuthAccount a = account(NOW.plusSeconds(60), null);
        UserOAuthAccount b = account(NOW.plusSeconds(120), null);
        givenSinglePage(a, b);
        when(hiemdall.refresh(REFRESH_TOKEN)).thenReturn(success("ya29.new", null));
        when(persistence.storeRefreshed(eq(a.oauthAccountId()), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("db hiccup"));

        sweeper.sweep();

        verify(hiemdall, times(2)).refresh(REFRESH_TOKEN);
        verify(locks).unlock(a.oauthAccountId());
        verify(locks).unlock(b.oauthAccountId());
        assertThat(counter("sweep", "transient_failure")).isEqualTo(1.0);
        assertThat(counter("sweep", "refreshed")).isEqualTo(1.0);
    }

    @Test
    void decryptFailure_recordsAndContinues() {
        UserOAuthAccount bad = account(NOW.plusSeconds(60), null, "1//undecryptable");
        UserOAuthAccount good = account(NOW.plusSeconds(120), null);
        givenSinglePage(bad, good);
        when(cipher.decrypt(bad.refreshTokenEnc(), bad.refreshTokenNonce()))
                .thenThrow(new TokenCipherException("bad", null));
        when(hiemdall.refresh(REFRESH_TOKEN)).thenReturn(success("ya29.new", null));

        sweeper.sweep();

        verify(persistence).recordFailure(bad.oauthAccountId(), RefreshFailureCode.DECRYPT_FAILED);
        verify(hiemdall, times(1)).refresh(REFRESH_TOKEN);
    }

    @Test
    void queryFailure_isSwallowed() {
        when(accounts.findDueForRefresh(any(), any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("db down"));

        assertThatCode(sweeper::sweep).doesNotThrowAnyException();
    }

    @Test
    void emptyResult_doesNothing() {
        when(accounts.findDueForRefresh(any(), any(), any(), anyInt())).thenReturn(List.of());

        sweeper.sweep();

        verifyNoInteractions(hiemdall, persistence, locks);
    }

    /** First page returns {@code rows}; any later cursor page is empty (mock default). */
    private void givenSinglePage(UserOAuthAccount... rows) {
        when(accounts.findDueForRefresh(any(), eq(Instant.EPOCH), any(), anyInt()))
                .thenReturn(List.of(rows));
    }
}
