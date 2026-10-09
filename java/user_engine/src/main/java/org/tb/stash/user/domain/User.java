package org.tb.stash.user.domain;

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

    /** Factory for a brand-new, DB-unsaved user (ids + timestamps left for the DB). */
    public static User newUser(String primaryEmail, String displayName, String pictureUrl) {
        return new User(null, primaryEmail, displayName, pictureUrl, STATUS_ACTIVE, null, null);
    }
}
