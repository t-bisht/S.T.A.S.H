/**
 * Entity records mapped to the STASH identity tables.
 *
 * <p>Pure data: no behavior, no framework ties beyond Spring Data JDBC's {@code @Table} /
 * {@code @Id} annotations. Every public type is a {@code record}. Consumed by {@code persistence}
 * (repositories), {@code login} (upsert flow), {@code session} (cookie filter), and {@code me}
 * (profile response).
 *
 * <p>Depends on nothing else in this module.
 */
package org.tb.stash.user.domain;
