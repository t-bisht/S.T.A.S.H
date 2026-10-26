package org.tb.stash.admin.catalog.domain;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /admin/api/catalog/categories}. */
public record AddCategoryRequest(
        @NotBlank(message = "Category name is required.") String categoryName) {}
