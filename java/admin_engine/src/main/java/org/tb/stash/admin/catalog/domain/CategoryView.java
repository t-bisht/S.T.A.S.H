package org.tb.stash.admin.catalog.domain;

/**
 * A main category that has at least one item in {@code digital_shopping_mart_catalog}, with the
 * number of catalog items under it. Serialised camelCase (Jackson default for records).
 */
public record CategoryView(int categoryId, String categoryName, long itemCount) {}
