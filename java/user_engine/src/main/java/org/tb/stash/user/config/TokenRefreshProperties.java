package org.tb.stash.user.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Policy for keeping stored Google access tokens valid.
 *
 * <p>Defaults live here (single source of truth); {@code application.yml} only exposes {@code
 * enabled}. The compact constructor enforces {@code refreshWindow > interval}: otherwise a token
 * could expire between two sweep runs, so startup fails instead.
 *
 * @param enabled run the scheduled sweep; {@code false} disables the sweep only — on-demand refresh
 *     stays available. Env {@code TOKEN_REFRESH_ENABLED}.
 * @param interval fixed delay between sweep runs; at least one minute.
 * @param initialDelay delay before the first sweep after startup.
 * @param refreshWindow a token is refreshed by the sweep once it expires within this window.
 * @param pageSize rows fetched per keyset page during a sweep.
 * @param coalesceWindow an on-demand refresh within this window of the last successful refresh
 *     returns the stored token instead of calling Hiemdall again.
 * @param lockWait how long an on-demand refresh waits for a concurrent refresh of the same account.
 * @param minValidity the minimum life an access token must have left to be handed to an engine
 *     (spec 02). A token within this window of expiry is refreshed inline first, so the engine
 *     never receives one that dies mid-call. Must be strictly less than {@code refreshWindow}.
 */
@Validated
@ConfigurationProperties(prefix = "token-refresh")
public record TokenRefreshProperties(
        @DefaultValue("true") boolean enabled,
        @NotNull @DurationMin(minutes = 1) @DefaultValue("PT10M") Duration interval,
        @NotNull @DefaultValue("PT1M") Duration initialDelay,
        @NotNull @DefaultValue("PT15M") Duration refreshWindow,
        @Min(1) @Max(500) @DefaultValue("50") int pageSize,
        @NotNull @DefaultValue("PT30S") Duration coalesceWindow,
        @NotNull @DefaultValue("PT10S") Duration lockWait,
        @NotNull @DefaultValue("PT2M") Duration minValidity) {

    public TokenRefreshProperties {
        // Null checks are left to @NotNull so the binder reports them with the property name.
        if (interval != null && refreshWindow != null && refreshWindow.compareTo(interval) <= 0) {
            throw new IllegalArgumentException(
                    "token-refresh.refresh-window ("
                            + refreshWindow
                            + ") must be greater than token-refresh.interval ("
                            + interval
                            + ")");
        }
        if (minValidity != null
                && refreshWindow != null
                && minValidity.compareTo(refreshWindow) >= 0) {
            throw new IllegalArgumentException(
                    "token-refresh.min-validity ("
                            + minValidity
                            + ") must be less than token-refresh.refresh-window ("
                            + refreshWindow
                            + ")");
        }
    }
}
