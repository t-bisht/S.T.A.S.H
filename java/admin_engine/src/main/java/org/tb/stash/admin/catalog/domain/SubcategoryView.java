package org.tb.stash.admin.catalog.domain;

/**
 * A leaf subcategory (within one category) that has at least one catalog item, with its item count.
 * Serialised camelCase.
 */
public record SubcategoryView(int subcategoryId, String subcategoryName, long itemCount) {}
