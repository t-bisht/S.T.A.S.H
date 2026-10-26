package org.tb.stash.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Admin console service (login only). Port 7084 (loopback-only in compose).
 *
 * <p>Property beans are registered in {@link org.tb.stash.admin.config.AdminEngineConfig}, matching
 * {@code UserEngineApplication} — this class is the entry point and nothing else.
 */
@SpringBootApplication
public class AdminEngineApplication {

    private static final Logger log = LoggerFactory.getLogger(AdminEngineApplication.class);

    public static void main(String[] args) {
        log.info("admin-engine starting on 0.0.0.0:7084");
        SpringApplication.run(AdminEngineApplication.class, args);
    }
}
