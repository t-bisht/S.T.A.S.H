/**
 * Read-only product-catalog browser (database.md §4). A two-level drill-down — categories →
 * subcategories → items — over {@code digital_shopping_mart_catalog} and the shared taxonomy
 * tables. No mutation, cascade, or audit in this module: those were removed in the admin overhaul.
 *
 * <p>Standard feature split: {@code controller} (HTTP) → {@code service} (boundary) → {@code crud}
 * (JdbcTemplate reads), with immutable {@code domain} view records.
 */
package org.tb.stash.admin.catalog;
