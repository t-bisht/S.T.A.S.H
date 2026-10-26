package org.tb.stash.admin.catalog.domain;

import java.util.List;

/**
 * A category with ALL its subcategories — the move-target picker's data (not item-count filtered).
 */
public record TaxonomyCategory(
        int categoryId, String categoryName, List<TaxonomySubcategory> subcategories) {}
