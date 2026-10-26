package org.tb.stash.admin.catalog.service;

import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.tb.stash.admin.catalog.crud.CatalogPersistence;
import org.tb.stash.admin.catalog.domain.CatalogItemView;
import org.tb.stash.admin.catalog.domain.CategoryRef;
import org.tb.stash.admin.catalog.domain.CategoryView;
import org.tb.stash.admin.catalog.domain.ItemUpdateResult;
import org.tb.stash.admin.catalog.domain.ResolvedSubcategory;
import org.tb.stash.admin.catalog.domain.SubcategoryView;
import org.tb.stash.admin.catalog.domain.TaxonomyCategory;
import org.tb.stash.admin.catalog.domain.TaxonomySubcategory;
import org.tb.stash.admin.catalog.exception.DuplicateNameException;
import org.tb.stash.admin.catalog.exception.InvalidSubcategoryException;
import org.tb.stash.admin.catalog.exception.NotFoundException;

/**
 * Catalog browse + edit service. Browse is a thin pass-through; the mutations carry the validation
 * the UI relies on for its messages:
 *
 * <ul>
 *   <li>names are trimmed; an all-whitespace name is rejected as blank (409-vs-400 split below).
 *   <li>a duplicate category/subcategory name → {@link DuplicateNameException} (409). The UNIQUE
 *       constraint is the source of truth: a {@link DuplicateKeyException} from the insert is
 *       translated, so a race still yields the clean message.
 *   <li>adding a subcategory to a missing category → {@link NotFoundException} (404).
 *   <li>moving an item to a non-existent subcategory → {@link InvalidSubcategoryException} (400); a
 *       missing catalog item → {@link NotFoundException} (404).
 * </ul>
 */
@Service
public class CatalogService {

    private final CatalogPersistence persistence;

    public CatalogService(CatalogPersistence persistence) {
        this.persistence = persistence;
    }

    public List<CategoryView> categories() {
        return persistence.listCategories();
    }

    public List<SubcategoryView> subcategories(int categoryId) {
        return persistence.listSubcategories(categoryId);
    }

    public List<CatalogItemView> items(int subcategoryId) {
        return persistence.listItems(subcategoryId);
    }

    /** The full category→subcategory tree for the item move-target picker. */
    public List<TaxonomyCategory> taxonomy() {
        return persistence.listTaxonomy();
    }

    public CategoryView addCategory(String rawName) {
        String name = clean(rawName, "Category name is required.");
        try {
            return persistence.insertCategory(name);
        } catch (DuplicateKeyException e) {
            throw new DuplicateNameException("A category named \"" + name + "\" already exists.");
        }
    }

    public SubcategoryView addSubcategory(int categoryId, String rawName) {
        String name = clean(rawName, "Subcategory name is required.");
        if (!persistence.categoryExists(categoryId)) {
            throw new NotFoundException("Category " + categoryId + " does not exist.");
        }
        try {
            return persistence.insertSubcategory(categoryId, name);
        } catch (DuplicateKeyException e) {
            // subcategory_name is GLOBALLY unique (05_item_categories.sql).
            throw new DuplicateNameException(
                    "A subcategory named \"" + name + "\" already exists.");
        }
    }

    public CategoryRef renameCategory(int categoryId, String rawName) {
        String name = clean(rawName, "Category name is required.");
        try {
            return persistence
                    .renameCategory(categoryId, name)
                    .orElseThrow(
                            () ->
                                    new NotFoundException(
                                            "Category " + categoryId + " does not exist."));
        } catch (DuplicateKeyException e) {
            throw new DuplicateNameException("A category named \"" + name + "\" already exists.");
        }
    }

    public TaxonomySubcategory renameSubcategory(int subcategoryId, String rawName) {
        String name = clean(rawName, "Subcategory name is required.");
        try {
            return persistence
                    .renameSubcategory(subcategoryId, name)
                    .orElseThrow(
                            () ->
                                    new NotFoundException(
                                            "Subcategory " + subcategoryId + " does not exist."));
        } catch (DuplicateKeyException e) {
            throw new DuplicateNameException(
                    "A subcategory named \"" + name + "\" already exists.");
        }
    }

    public ItemUpdateResult updateItemSubcategory(int catalogId, int subcategoryId) {
        if (!persistence.itemExists(catalogId)) {
            throw new NotFoundException("Catalog item " + catalogId + " does not exist.");
        }
        ResolvedSubcategory target =
                persistence
                        .resolveSubcategory(subcategoryId)
                        .orElseThrow(
                                () ->
                                        new InvalidSubcategoryException(
                                                "Subcategory "
                                                        + subcategoryId
                                                        + " does not exist."));
        String itemName =
                persistence.updateItemSubcategory(
                        catalogId, target.subcategoryId(), target.categoryId());
        return new ItemUpdateResult(
                catalogId,
                itemName,
                target.subcategoryId(),
                target.subcategoryName(),
                target.categoryId(),
                target.categoryName());
    }

    /** Trims a name and rejects a blank one. {@code @NotBlank} catches most; this guards " ". */
    private static String clean(String raw, String blankMessage) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException(blankMessage);
        }
        return name;
    }
}
