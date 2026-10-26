/**
 * {@code @ConfigurationProperties} beans and framework configuration.
 *
 * <p>Holds the externalised-config beans ({@link
 * org.tb.stash.admin.config.AdminCredentialsProperties}, {@link
 * org.tb.stash.admin.config.SessionProperties}) and {@link
 * org.tb.stash.admin.config.AdminEngineConfig}, which registers them with
 * {@code @EnableConfigurationProperties} (the user_engine pattern — never
 * {@code @ConfigurationPropertiesScan}) and exposes the {@code Clock} bean. Fail-fast validation
 * lives on each bean; a blank credential surfaces as a startup failure.
 *
 * <p>{@link org.tb.stash.admin.config.AdminWebMvcConfig} wires the {@code /admin/api/**} session
 * interceptor. It is kept apart from {@code AdminEngineConfig} on purpose: a bean-producing
 * {@code @Configuration} that also injects {@code SessionInterceptor} closes a dependency cycle
 * through {@code AdminAuthService}.
 */
package org.tb.stash.admin.config;
