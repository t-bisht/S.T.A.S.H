/**
 * Spring Data JDBC repositories for the identity tables.
 *
 * <p>Thin layer: {@link org.springframework.data.repository.CrudRepository} extensions plus a few
 * hand-rolled {@code @Query} methods for hot lookups ({@code findByProviderAndProviderSub}, {@code
 * findActive}). All multi-table / cross-aggregate work lives in {@code login} or {@code session},
 * not here.
 *
 * <p>Depends on {@code domain} only.
 */
package org.tb.stash.user.core.crud;
