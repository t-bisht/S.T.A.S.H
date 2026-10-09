package org.tb.stash.user.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Registers {@code @ConfigurationProperties} beans and shared infrastructure (e.g. {@link
 * RestClient}) for the user_engine.
 */
@Configuration
@EnableConfigurationProperties({
    HiemdallProperties.class,
    SessionProperties.class,
    TokenEncryptionProperties.class
})
public class UserEngineConfig {

    /** Shared {@link RestClient} for outbound HTTP (currently: Hiemdall exchange). */
    @Bean
    public RestClient restClient() {
        return RestClient.builder().build();
    }
}
