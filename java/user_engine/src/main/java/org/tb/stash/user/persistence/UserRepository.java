package org.tb.stash.user.persistence;

import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.tb.stash.user.domain.User;

/** Spring Data JDBC repository for {@link User}. */
public interface UserRepository extends CrudRepository<User, UUID> {}
