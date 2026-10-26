package org.tb.stash.admin.catalog.domain;

/** A subcategory with its parent category resolved — used to validate + apply an item move. */
public record ResolvedSubcategory(
        int subcategoryId, String subcategoryName, int categoryId, String categoryName) {}
