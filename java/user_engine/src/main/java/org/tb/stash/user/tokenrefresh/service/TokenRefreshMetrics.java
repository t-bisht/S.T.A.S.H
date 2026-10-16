package org.tb.stash.user.tokenrefresh.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.tb.stash.user.tokenrefresh.domain.AccountRefreshStatus;
import org.tb.stash.user.tokenrefresh.domain.RefreshTrigger;

/**
 * Micrometer instruments of the token-refresh feature.
 *
 * <ul>
 *   <li>Counter {@code stash.token_refresh}, tags {@code trigger} and {@code outcome}.
 *   <li>Timer {@code stash.token_refresh.hiemdall} around each Hiemdall call.
 * </ul>
 */
@Component
public class TokenRefreshMetrics {

    static final String COUNTER = "stash.token_refresh";
    static final String HIEMDALL_TIMER = "stash.token_refresh.hiemdall";

    private final MeterRegistry registry;
    private final Timer hiemdallTimer;

    public TokenRefreshMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.hiemdallTimer = Timer.builder(HIEMDALL_TIMER).register(registry);
    }

    /**
     * Counts one per-account refresh result.
     *
     * @param trigger what started the refresh
     * @param status how it ended
     */
    public void record(RefreshTrigger trigger, AccountRefreshStatus status) {
        registry.counter(COUNTER, "trigger", trigger.tag(), "outcome", status.tag()).increment();
    }

    /**
     * Runs {@code call} under the Hiemdall timer.
     *
     * @param call the Hiemdall call
     * @param <T> result type
     * @return {@code call}'s result
     */
    public <T> T timeHiemdall(Supplier<T> call) {
        return hiemdallTimer.record(call);
    }
}
