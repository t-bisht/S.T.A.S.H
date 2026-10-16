package org.tb.stash.user.tokenrefresh.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.tb.stash.user.config.TokenRefreshProperties;

/**
 * Runs {@link TokenRefreshService#sweep()} with a fixed delay of {@code token-refresh.interval}
 * after an initial {@code token-refresh.initial-delay}.
 *
 * <p>Registered programmatically (rather than {@code @Scheduled} placeholders) so the timings come
 * from the validated {@link TokenRefreshProperties}, whose defaults are the single source of truth.
 * Fixed <em>delay</em> means runs never overlap. Absent when {@code token-refresh.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "token-refresh.enabled", havingValue = "true", matchIfMissing = true)
public class TokenRefreshScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(TokenRefreshScheduler.class);

    private final TokenRefreshService service;
    private final TokenRefreshProperties props;

    public TokenRefreshScheduler(TokenRefreshService service, TokenRefreshProperties props) {
        this.service = service;
        this.props = props;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(
                new FixedDelayTask(this::runSweep, props.interval(), props.initialDelay()));
        log.info(
                "token_refresh.scheduler.registered interval={} initialDelay={} window={}",
                props.interval(),
                props.initialDelay(),
                props.refreshWindow());
    }

    /** Never throws: an escaped exception would not stop the schedule, but must be logged. */
    void runSweep() {
        try {
            service.sweep();
        } catch (RuntimeException e) {
            log.error("token_refresh.sweep.crashed", e);
        }
    }
}
