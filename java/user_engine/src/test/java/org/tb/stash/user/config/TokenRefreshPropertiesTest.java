package org.tb.stash.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class TokenRefreshPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                    .withUserConfiguration(PropsConfig.class);

    @Test
    void defaults_bindWhenNothingConfigured() {
        runner.run(
                context -> {
                    TokenRefreshProperties props = context.getBean(TokenRefreshProperties.class);
                    assertThat(props.enabled()).isTrue();
                    assertThat(props.interval()).isEqualTo(Duration.ofMinutes(10));
                    assertThat(props.initialDelay()).isEqualTo(Duration.ofMinutes(1));
                    assertThat(props.refreshWindow()).isEqualTo(Duration.ofMinutes(15));
                    assertThat(props.pageSize()).isEqualTo(50);
                    assertThat(props.coalesceWindow()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(props.lockWait()).isEqualTo(Duration.ofSeconds(10));
                });
    }

    @Test
    void refreshWindowEqualToInterval_failsStartup() {
        runner.withPropertyValues(
                        "token-refresh.interval=PT10M", "token-refresh.refresh-window=PT10M")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refreshWindowShorterThanInterval_failsStartup() {
        runner.withPropertyValues(
                        "token-refresh.interval=PT10M", "token-refresh.refresh-window=PT5M")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void intervalBelowOneMinute_failsStartup() {
        runner.withPropertyValues(
                        "token-refresh.interval=PT30S", "token-refresh.refresh-window=PT15M")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void pageSizeOutOfRange_failsStartup() {
        runner.withPropertyValues("token-refresh.page-size=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("token-refresh.page-size=501")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TokenRefreshProperties.class)
    static class PropsConfig {}
}
