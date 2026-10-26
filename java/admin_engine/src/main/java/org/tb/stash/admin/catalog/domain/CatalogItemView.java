package org.tb.stash.admin.catalog.domain;

/**
 * One row of {@code digital_shopping_mart_catalog} as shown in the browser: the catalog id and the
 * product name. Serialised camelCase.
 */
public record CatalogItemView(int catalogId, String itemName) {}
