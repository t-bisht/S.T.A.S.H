package org.tb.stash.user.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.tb.stash.user.domain.UserOAuthAccount;

/**
 * Spring Data JDBC repository for {@link UserOAuthAccount}.
 *
 * <p>Lookup by {@code (provider, provider_sub)} is the hot path during login (first vs returning
 * user decision).
 */
public interface UserOAuthAccountRepository extends CrudRepository<UserOAuthAccount, UUID> {

    Optional<UserOAuthAccount> findByProviderAndProviderSub(String provider, String providerSub);
}
