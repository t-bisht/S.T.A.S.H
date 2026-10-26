package org.tb.stash.admin.catalog.domain;

/**
 * The item after its subcategory was changed, with the resolved subcategory + (derived) category so
 * the UI can re-render the row without a second fetch.
 */
public record ItemUpdateResult(
        int catalogId,
        String itemName,
        int subcategoryId,
        String subcategoryName,
        int categoryId,
        String categoryName) {}
