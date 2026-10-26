package org.tb.stash.admin.catalog.domain;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /admin/api/catalog/categories/{categoryId}/subcategories}. */
public record AddSubcategoryRequest(
        @NotBlank(message = "Subcategory name is required.") String subcategoryName) {}
