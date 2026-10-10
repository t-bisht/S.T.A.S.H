package org.tb.stash.user.core.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Opaque server-side session row.
 *
 * <p>Mapped to {@code sessions}. The {@code sessionHandle} is a SecureRandom base64url string (≥32
 * bytes of entropy) that the client receives inside the {@code STASH_SESSION} httpOnly cookie.
 *
 * <p>Temporary structure: deprecated the day Hiemdall-issued JWT sessions ship. See {@code
 * hiemdall/docs/design/spec/jwt_token_issuance.md}.
 *
 * <p>Phase 2 does not write {@code lastSeenAt} on reads (see parent spec Q14); the column exists
 * for future activity-tracking.
 *
 * <p>Implements {@link Persistable} with {@code isNew() == true} because the handle is minted
 * client-side (non-null when {@code save} is called), which would otherwise make Spring Data JDBC
 * route the call to UPDATE and fail with zero affected rows. Session rows are append-only in Phase
 * 2 (mint on login, delete on logout), so always-insert matches the actual usage pattern. If
 * mid-session updates get added later, swap this for a {@link
 * org.springframework.data.relational.core.conversion.AggregateChange}-based flow or a dedicated
 * update query.
 */
@Table("sessions")
public record Session(
        @Id String sessionHandle,
        UUID userId,
        Instant issuedAt,
        Instant lastSeenAt,
        Instant expiresAt)
        implements Persistable<String> {

    @Override
    public String getId() {
        return sessionHandle;
    }

    @Override
    public boolean isNew() {
        return true;
    }
}
