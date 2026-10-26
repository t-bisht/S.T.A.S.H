package org.tb.stash.admin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.tb.stash.admin.auth.controller.SessionInterceptor;

/**
 * Web-MVC wiring for admin_engine: registers the {@link SessionInterceptor} on {@code
 * /admin/api/**} only, so {@code /admin/login}, {@code /admin/logout}, static assets and {@code
 * /actuator/**} stay open.
 *
 * <p>Deliberately separate from {@link AdminEngineConfig}: this class only <i>consumes</i> beans
 * and declares none, so nothing upstream of {@code SessionInterceptor} can depend on it. Merging
 * the two reintroduces the {@code AdminAuthService} → config → {@code SessionInterceptor} cycle.
 */
@Configuration
public class AdminWebMvcConfig implements WebMvcConfigurer {

    private final SessionInterceptor sessionInterceptor;

    public AdminWebMvcConfig(SessionInterceptor sessionInterceptor) {
        this.sessionInterceptor = sessionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sessionInterceptor).addPathPatterns("/admin/api/**");
    }
}
