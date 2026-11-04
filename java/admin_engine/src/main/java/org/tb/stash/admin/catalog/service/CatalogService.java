package org.tb.stash.admin.catalog.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.tb.stash.admin.auth.controller.SessionInterceptor;
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
import org.tb.stash.admin.catalog.exception.InUseException;
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

    /**
     * Admin-action audit trail. Every mutation below emits one {@code admin.catalog.*} line stamped
     * with the acting admin so the engine log carries who changed the catalog and how. Reads are
     * not logged. This is a log-only trail — there is no audit table (removed in the admin
     * overhaul, see {@code catalog/package-info.java}).
     */
    private static final Logger audit = LoggerFactory.getLogger("stash.admin.audit");

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
            CategoryView created = persistence.insertCategory(name);
            audit.info(
                    "admin.catalog.category.add actor={} id={} name=\"{}\"",
                    actor(),
                    created.categoryId(),
                    created.categoryName());
            return created;
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
            SubcategoryView created = persistence.insertSubcategory(categoryId, name);
            audit.info(
                    "admin.catalog.subcategory.add actor={} id={} name=\"{}\" category={}",
                    actor(),
                    created.subcategoryId(),
                    created.subcategoryName(),
                    categoryId);
            return created;
        } catch (DuplicateKeyException e) {
            // subcategory_name is GLOBALLY unique (05_item_categories.sql).
            throw new DuplicateNameException(
                    "A subcategory named \"" + name + "\" already exists.");
        }
    }

    public CategoryRef renameCategory(int categoryId, String rawName) {
        String name = clean(rawName, "Category name is required.");
        try {
            CategoryRef renamed =
                    persistence
                            .renameCategory(categoryId, name)
                            .orElseThrow(
                                    () ->
                                            new NotFoundException(
                                                    "Category " + categoryId + " does not exist."));
            audit.info(
                    "admin.catalog.category.rename actor={} id={} name=\"{}\"",
                    actor(),
                    renamed.categoryId(),
                    renamed.categoryName());
            return renamed;
        } catch (DuplicateKeyException e) {
            throw new DuplicateNameException("A category named \"" + name + "\" already exists.");
        }
    }

    public TaxonomySubcategory renameSubcategory(int subcategoryId, String rawName) {
        String name = clean(rawName, "Subcategory name is required.");
        try {
            TaxonomySubcategory renamed =
                    persistence
                            .renameSubcategory(subcategoryId, name)
                            .orElseThrow(
                                    () ->
                                            new NotFoundException(
                                                    "Subcategory "
                                                            + subcategoryId
                                                            + " does not exist."));
            audit.info(
                    "admin.catalog.subcategory.rename actor={} id={} name=\"{}\"",
                    actor(),
                    renamed.subcategoryId(),
                    renamed.subcategoryName());
            return renamed;
        } catch (DuplicateKeyException e) {
            throw new DuplicateNameException(
                    "A subcategory named \"" + name + "\" already exists.");
        }
    }

    /**
     * Deletes a category. Blocked (409) while it still holds subcategories — the empty-the-children
     * rule the UI relies on. Unknown id → 404. A DB-level FK rejection (a reference we do not
     * pre-check) is also surfaced as a 409 rather than a 500.
     */
    public void deleteCategory(int categoryId) {
        if (!persistence.categoryExists(categoryId)) {
            throw new NotFoundException("Category " + categoryId + " does not exist.");
        }
        long subcategories = persistence.countSubcategories(categoryId);
        if (subcategories > 0) {
            audit.info(
                    "admin.catalog.category.delete actor={} id={} result=blocked"
                            + " reason=has_subcategories count={}",
                    actor(),
                    categoryId,
                    subcategories);
            throw new InUseException(
                    "Cannot delete a category that still has "
                            + subcategories
                            + " subcategory(ies). Delete or move them first.");
        }
        try {
            persistence.deleteCategory(categoryId);
        } catch (DataIntegrityViolationException e) {
            audit.info(
                    "admin.catalog.category.delete actor={} id={} result=blocked reason=fk_reference",
                    actor(),
                    categoryId);
            throw new InUseException(
                    "Cannot delete category " + categoryId + " — it is still referenced.");
        }
        audit.info(
                "admin.catalog.category.delete actor={} id={} result=deleted", actor(), categoryId);
    }

    /**
     * Deletes a subcategory. Blocked (409) while catalog items still sit under it ({@code
     * digital_shopping_mart_catalog}). Unknown id → 404. A DB-level FK rejection (e.g. {@code
     * online_retail_purchase_history} still points at it) is surfaced as a 409, not a 500.
     */
    public void deleteSubcategory(int subcategoryId) {
        if (!persistence.subcategoryExists(subcategoryId)) {
            throw new NotFoundException("Subcategory " + subcategoryId + " does not exist.");
        }
        long items = persistence.countCatalogItems(subcategoryId);
        if (items > 0) {
            audit.info(
                    "admin.catalog.subcategory.delete actor={} id={} result=blocked"
                            + " reason=has_items count={}",
                    actor(),
                    subcategoryId,
                    items);
            throw new InUseException(
                    "Cannot delete a subcategory that still has "
                            + items
                            + " item(s). Move them to another subcategory first.");
        }
        try {
            persistence.deleteSubcategory(subcategoryId);
        } catch (DataIntegrityViolationException e) {
            audit.info(
                    "admin.catalog.subcategory.delete actor={} id={} result=blocked"
                            + " reason=purchase_history_reference",
                    actor(),
                    subcategoryId);
            throw new InUseException(
                    "Cannot delete subcategory "
                            + subcategoryId
                            + " — it is still referenced by purchase history.");
        }
        audit.info(
                "admin.catalog.subcategory.delete actor={} id={} result=deleted",
                actor(),
                subcategoryId);
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
        audit.info(
                "admin.catalog.item.move actor={} id={} name=\"{}\" subcategory={} category={}",
                actor(),
                catalogId,
                itemName,
                target.subcategoryId(),
                target.categoryId());
        return new ItemUpdateResult(
                catalogId,
                itemName,
                target.subcategoryId(),
                target.subcategoryName(),
                target.categoryId(),
                target.categoryName());
    }

    /**
     * The acting admin for the audit line — the username {@code SessionInterceptor} stamped on the
     * current request. Resolved from the request context so the mutation signatures stay clean.
     * Falls back to {@code "unknown"} outside a request (e.g. a unit test).
     */
    private static String actor() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "unknown";
        }
        Object a =
                attrs.getAttribute(
                        SessionInterceptor.ACTOR_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return a == null ? "unknown" : a.toString();
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
