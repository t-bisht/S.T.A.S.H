/**
 * Product-catalog browser + editor (database.md §4). A two-level drill-down — categories →
 * subcategories → items — over {@code digital_shopping_mart_catalog} and the shared taxonomy
 * tables, with add / rename / delete on the taxonomy and an item-move. No DB cascade or audit table
 * (those were removed in the admin overhaul); mutations instead emit a log-only audit line ({@code
 * stash.admin.audit}) stamped with the acting admin.
 *
 * <p>Standard feature split: {@code controller} (HTTP) → {@code service} (boundary) → {@code crud}
 * (JdbcTemplate reads), with immutable {@code domain} view records.
 */
package org.tb.stash.admin.catalog;
