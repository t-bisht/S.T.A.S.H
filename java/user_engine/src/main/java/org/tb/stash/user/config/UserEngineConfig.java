package org.tb.stash.user.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/**
 * Registers {@code @ConfigurationProperties} beans and shared infrastructure (e.g. {@link
 * RestClient}, {@link Clock}) for the user_engine.
 *
 * <p>{@link EnableScheduling} backs the token-refresh sweep; the sweep itself is switched off with
 * {@code token-refresh.enabled=false}.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({
    HiemdallProperties.class,
    SessionProperties.class,
    TokenEncryptionProperties.class,
    TokenRefreshProperties.class
})
public class UserEngineConfig {

    /** Shared {@link RestClient} for outbound HTTP (Hiemdall exchange + token refresh). */
    @Bean
    public RestClient restClient() {
        return RestClient.builder().build();
    }

    /** UTC wall clock; injected (rather than {@code Instant.now()}) where tests need fixed time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
