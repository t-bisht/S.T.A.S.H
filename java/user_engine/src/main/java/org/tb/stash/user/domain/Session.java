package org.tb.stash.user.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
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
 */
@Table("sessions")
public record Session(
        @Id String sessionHandle,
        UUID userId,
        Instant issuedAt,
        Instant lastSeenAt,
        Instant expiresAt) {}
