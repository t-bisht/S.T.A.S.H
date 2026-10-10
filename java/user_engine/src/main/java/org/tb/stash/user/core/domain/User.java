package org.tb.stash.user.core.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One row per human user of STASH.
 *
 * <p>Mapped to {@code users}. Identity provider claims live on sibling {@link UserOAuthAccount}
 * rows — this table carries only the STASH-local identity.
 *
 * @param userId STASH-local UUID (DB-generated via {@code gen_random_uuid()}); {@code null} on an
 *     unsaved instance.
 * @param primaryEmail latest email observed across any linked OAuth account.
 * @param status {@code ACTIVE} or {@code DISABLED}; CHECK-constrained in DDL.
 */
@Table("users")
public record User(
        @Id UUID userId,
        String primaryEmail,
        String displayName,
        String pictureUrl,
        String status,
        Instant createdAt,
        Instant lastLoginAt) {

    public static final String STATUS_ACTIVE = "ACTIVE";

    /**
     * Factory for a brand-new, DB-unsaved user. The {@code user_id} is left null so {@code
     * gen_random_uuid()} fires on the DB side; timestamps are stamped here because Spring Data JDBC
     * sends every non-id column in the INSERT, which would send explicit {@code NULL} and defeat
     * the {@code DEFAULT now()} on {@code created_at}.
     */
    public static User newUser(String primaryEmail, String displayName, String pictureUrl) {
        Instant now = Instant.now();
        return new User(null, primaryEmail, displayName, pictureUrl, STATUS_ACTIVE, now, now);
    }
}
