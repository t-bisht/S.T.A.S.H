package org.tb.stash.user.tokenrefresh.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.tb.stash.user.config.TokenRefreshProperties;
import org.tb.stash.user.core.crud.UserOAuthAccountRepository;
import org.tb.stash.user.core.domain.UserOAuthAccount;
import org.tb.stash.user.tokenrefresh.domain.AccountRefreshStatus;
import org.tb.stash.user.tokenrefresh.domain.RefreshTrigger;

/**
 * Scheduled bulk refresh: refreshes every Google account whose access token expires within {@code
 * token-refresh.refresh-window}, decided from DB columns only. Run by {@link
 * TokenRefreshScheduler}.
 */
@Service
public class TokenRefreshSweeper {

    private static final Logger log = LoggerFactory.getLogger(TokenRefreshSweeper.class);
    private static final UUID NIL_UUID = new UUID(0L, 0L); // start with default UUID

    private final UserOAuthAccountRepository accounts;
    private final TokenExchanger exchanger;
    private final AccountLocks locks;
    private final TokenRefreshMetrics metrics;
    private final TokenRefreshProperties props;
    private final Clock clock;

    public TokenRefreshSweeper(
            UserOAuthAccountRepository accounts,
            TokenExchanger exchanger, // performs token refresh ops
            AccountLocks locks, // prevents concurrent refreshes
            TokenRefreshMetrics metrics, // recordign metrics
            TokenRefreshProperties props, // properties
            Clock clock) { // time related util
        this.accounts = accounts;
        this.exchanger = exchanger;
        this.locks = locks;
        this.metrics = metrics;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Refreshes every Google account whose access token expires before {@code now +
     * refresh-window}.
     *
     * <p>Rows are keyset-paged and processed sequentially. A failing row is counted and skipped;
     * the first {@link AccountRefreshStatus#CONFIG_ERROR} aborts the run since every row would fail
     * the same way. Busy accounts are skipped without waiting. Logs one summary line per run.
     *
     * <p>Never throws.
     */
    public void sweep() { // invoked by schedular
        long startNanos = System.nanoTime(); // stop watch start
        Map<AccountRefreshStatus, Integer> tally =
                new EnumMap<>(
                        AccountRefreshStatus
                                .class); // a tally map how many accounts were refreshed and how
        // many not
        try {
            runSweep(tally);
        } catch (RuntimeException e) {
            log.error("token_refresh.sweep.failed", e);
        }
        logSummary(tally, Duration.ofNanos(System.nanoTime() - startNanos));
    }

    private void runSweep(Map<AccountRefreshStatus, Integer> tally) {
        Instant threshold =
                clock.instant().plus(props.refreshWindow()); // current time refresh window (refresh
        // is the life to live is within this
        // window)
        Instant afterExpiry = Instant.EPOCH;
        UUID afterId = NIL_UUID;
        List<UserOAuthAccount> page;
        do {
            // fetch accounts that are due to expiry within the window (one problem here is the
            // query has hardcoded google)
            page = accounts.findDueForRefresh(threshold, afterExpiry, afterId, props.pageSize());
            // props.pagesize is for pagination (kind of an over kills might now have been required
            // but ok )
            for (UserOAuthAccount row : page) {
                AccountRefreshStatus status = sweepAccount(row);
                tally.merge(status, 1, Integer::sum);
                metrics.record(RefreshTrigger.SWEEP, status);
                if (status == AccountRefreshStatus.CONFIG_ERROR) {
                    log.error("token_refresh.sweep.aborted reason=config_error");
                    return;
                }
            }
            if (!page.isEmpty()) {
                UserOAuthAccount last = page.getLast();
                afterExpiry = last.accessTokenExpiresAt();
                afterId = last.oauthAccountId(); // mark the last ID and start from there in next
                // interation
            }
        } while (page.size() == props.pageSize());
    }

    /** Refreshes one due account; never throws. */
    private AccountRefreshStatus sweepAccount(UserOAuthAccount account) {
        UUID accountId = account.oauthAccountId();
        if (!locks.tryLock(accountId, Duration.ZERO)) {
            log.debug("token_refresh.sweep.busy oauthAccountId={}", accountId);
            return AccountRefreshStatus.SKIPPED_BUSY;
        }
        try {
            return exchanger.exchange(account).status();
        } catch (RuntimeException e) {
            log.error("token_refresh.sweep.account_failed oauthAccountId={}", accountId, e);
            return AccountRefreshStatus.TRANSIENT_FAILURE;
        } finally {
            locks.unlock(accountId);
        }
    }

    private static void logSummary(Map<AccountRefreshStatus, Integer> tally, Duration took) {
        log.info(
                "token_refresh.sweep refreshed={} skipped={} logged_out={} transient={}"
                        + " config_error={} duration_ms={}",
                tally.getOrDefault(AccountRefreshStatus.REFRESHED, 0),
                tally.getOrDefault(AccountRefreshStatus.SKIPPED_FRESH, 0)
                        + tally.getOrDefault(AccountRefreshStatus.SKIPPED_BUSY, 0),
                tally.getOrDefault(AccountRefreshStatus.LOGGED_OUT, 0),
                tally.getOrDefault(AccountRefreshStatus.TRANSIENT_FAILURE, 0),
                tally.getOrDefault(AccountRefreshStatus.CONFIG_ERROR, 0),
                took.toMillis());
    }
}
