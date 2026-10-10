package org.tb.stash.user.core.crud;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.tb.stash.user.core.domain.Session;

/**
 * Spring Data JDBC repository for {@link Session}.
 *
 * <p>The hot path is {@link #findActive(String, Instant)}: fetch by handle and reject expired rows
 * in a single round-trip. Expired rows accumulate in-table — a sweeper is deferred (see parent spec
 * Qnew-1).
 */
public interface SessionRepository extends CrudRepository<Session, String> {

    @Query("SELECT * FROM sessions WHERE session_handle = :handle AND expires_at > :now")
    Optional<Session> findActive(@Param("handle") String handle, @Param("now") Instant now);
}
