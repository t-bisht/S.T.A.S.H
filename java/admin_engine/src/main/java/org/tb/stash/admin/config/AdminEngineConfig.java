package org.tb.stash.admin.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@code @ConfigurationProperties} beans and shared infrastructure for admin_engine.
 *
 * <p>Uses {@link EnableConfigurationProperties} — the user_engine pattern ({@code
 * UserEngineConfig}), never {@code @ConfigurationPropertiesScan}. Also exposes the {@link Clock}
 * bean so session TTL can be tested with fixed time.
 *
 * <p><b>Why this class has no constructor dependencies.</b> It <i>produces</i> beans that services
 * consume ({@code Clock} → {@code AdminAuthService}), so it must not consume a bean that depends on
 * those services. Injecting {@code SessionInterceptor} here closed a cycle ({@code
 * AdminAuthService} → {@code AdminEngineConfig} → {@code SessionInterceptor} → {@code
 * AdminAuthService}) and the context refused to refresh. Web-MVC wiring therefore lives in {@link
 * AdminWebMvcConfig}, which exports nothing.
 */
@Configuration
@EnableConfigurationProperties({AdminCredentialsProperties.class, SessionProperties.class})
public class AdminEngineConfig {

    /** UTC wall clock; injected (rather than {@code Instant.now()}) where tests need fixed time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
