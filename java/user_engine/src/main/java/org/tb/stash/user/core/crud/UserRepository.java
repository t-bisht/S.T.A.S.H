package org.tb.stash.user.core.crud;

import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.tb.stash.user.core.domain.User;

/** Spring Data JDBC repository for {@link User}. */
public interface UserRepository extends CrudRepository<User, UUID> {}
