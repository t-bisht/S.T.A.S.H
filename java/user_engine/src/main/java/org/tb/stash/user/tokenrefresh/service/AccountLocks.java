package org.tb.stash.user.tokenrefresh.service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/**
 * In-process, per-account mutual exclusion so one OAuth account is never refreshed twice at once
 * (sweep vs on-demand, or two on-demand callers).
 *
 * <p>Valid only while user_engine runs as a single instance; multiple replicas would need a DB or
 * distributed lock. One {@link ReentrantLock} is kept per account ever locked — bounded by the
 * number of linked accounts, which is small.
 */
@Component
public class AccountLocks {

    private final ConcurrentMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Tries to acquire the account's lock.
     *
     * @param accountId account to lock
     * @param wait how long to wait; {@link Duration#ZERO} returns immediately
     * @return {@code true} if acquired — the caller must then {@link #unlock} in {@code finally};
     *     {@code false} on timeout or interruption (interrupt flag restored)
     */
    public boolean tryLock(UUID accountId, Duration wait) {
        ReentrantLock lock = locks.computeIfAbsent(accountId, id -> new ReentrantLock());
        if (wait.isZero() || wait.isNegative()) {
            return lock.tryLock();
        }
        try {
            return lock.tryLock(wait.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Releases the account's lock if the current thread holds it; otherwise a no-op, so it is safe
     * in {@code finally} blocks.
     *
     * @param accountId account to unlock
     */
    public void unlock(UUID accountId) {
        ReentrantLock lock = locks.get(accountId);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
