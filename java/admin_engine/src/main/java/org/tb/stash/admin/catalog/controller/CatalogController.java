package org.tb.stash.admin.catalog.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tb.stash.admin.catalog.domain.AddCategoryRequest;
import org.tb.stash.admin.catalog.domain.AddSubcategoryRequest;
import org.tb.stash.admin.catalog.domain.CatalogItemView;
import org.tb.stash.admin.catalog.domain.CategoryRef;
import org.tb.stash.admin.catalog.domain.CategoryView;
import org.tb.stash.admin.catalog.domain.ItemUpdateResult;
import org.tb.stash.admin.catalog.domain.SubcategoryView;
import org.tb.stash.admin.catalog.domain.TaxonomyCategory;
import org.tb.stash.admin.catalog.domain.TaxonomySubcategory;
import org.tb.stash.admin.catalog.domain.UpdateItemRequest;
import org.tb.stash.admin.catalog.service.CatalogService;

/**
 * Catalog browse + edit surface, under {@code /admin/api/**} so the {@code SessionInterceptor}
 * guards it.
 *
 * <ul>
 *   <li>{@code GET /categories} — categories holding items.
 *   <li>{@code POST /categories} — add a category.
 *   <li>{@code GET /categories/{categoryId}/subcategories} — its subcategories.
 *   <li>{@code POST /categories/{categoryId}/subcategories} — add a subcategory under it.
 *   <li>{@code GET /subcategories/{subcategoryId}/items} — the items.
 *   <li>{@code PATCH /items/{catalogId}} — move an item to another subcategory.
 * </ul>
 *
 * <p>On a browse read an unknown id yields an empty list, not a 404. Edit errors are mapped to the
 * flat {@code {code, message}} body by {@code AdminErrorAdvice}.
 */
@RestController
@RequestMapping("/admin/api/catalog")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    public List<CategoryView> categories() {
        return service.categories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView addCategory(@Valid @RequestBody AddCategoryRequest body) {
        return service.addCategory(body.categoryName());
    }

    @PatchMapping("/categories/{categoryId}")
    public CategoryRef renameCategory(
            @PathVariable int categoryId, @Valid @RequestBody AddCategoryRequest body) {
        return service.renameCategory(categoryId, body.categoryName());
    }

    @GetMapping("/categories/{categoryId}/subcategories")
    public List<SubcategoryView> subcategories(@PathVariable int categoryId) {
        return service.subcategories(categoryId);
    }

    @PostMapping("/categories/{categoryId}/subcategories")
    @ResponseStatus(HttpStatus.CREATED)
    public SubcategoryView addSubcategory(
            @PathVariable int categoryId, @Valid @RequestBody AddSubcategoryRequest body) {
        return service.addSubcategory(categoryId, body.subcategoryName());
    }

    @PatchMapping("/subcategories/{subcategoryId}")
    public TaxonomySubcategory renameSubcategory(
            @PathVariable int subcategoryId, @Valid @RequestBody AddSubcategoryRequest body) {
        return service.renameSubcategory(subcategoryId, body.subcategoryName());
    }

    @GetMapping("/subcategories/{subcategoryId}/items")
    public List<CatalogItemView> items(@PathVariable int subcategoryId) {
        return service.items(subcategoryId);
    }

    /** Full category→subcategory tree (every row, not item-count filtered) for the move picker. */
    @GetMapping("/taxonomy")
    public List<TaxonomyCategory> taxonomy() {
        return service.taxonomy();
    }

    @PatchMapping("/items/{catalogId}")
    public ItemUpdateResult updateItem(
            @PathVariable int catalogId, @Valid @RequestBody UpdateItemRequest body) {
        return service.updateItemSubcategory(catalogId, body.subcategoryId());
    }
}
