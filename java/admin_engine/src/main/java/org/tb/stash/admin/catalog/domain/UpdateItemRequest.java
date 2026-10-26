package org.tb.stash.admin.catalog.domain;

import jakarta.validation.constraints.NotNull;

/** Body of {@code PATCH /admin/api/catalog/items/{catalogId}}. */
public record UpdateItemRequest(
        @NotNull(message = "A target subcategory id is required.") Integer subcategoryId) {}
