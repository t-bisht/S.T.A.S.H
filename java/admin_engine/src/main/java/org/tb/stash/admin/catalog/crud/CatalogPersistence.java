package org.tb.stash.admin.catalog.crud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.tb.stash.admin.catalog.domain.CatalogItemView;
import org.tb.stash.admin.catalog.domain.CategoryRef;
import org.tb.stash.admin.catalog.domain.CategoryView;
import org.tb.stash.admin.catalog.domain.ResolvedSubcategory;
import org.tb.stash.admin.catalog.domain.SubcategoryView;
import org.tb.stash.admin.catalog.domain.TaxonomyCategory;
import org.tb.stash.admin.catalog.domain.TaxonomySubcategory;

/**
 * Catalog data access. The browse reads are keyed through {@code digital_shopping_mart_catalog}
 * (07_admin_catalog.sql) and its FKs into {@code item_categories} / {@code item_subcategories}
 * (05_item_categories.sql). The mutations write those two taxonomy tables and move an item between
 * subcategories.
 *
 * <p>Pure {@link JdbcTemplate}: a flat two-level taxonomy over one table needs no ORM. Inserts use
 * {@code RETURNING} so the generated row comes back in one round-trip. A UNIQUE-name collision
 * surfaces as Spring's {@code DuplicateKeyException}; the service maps it to a typed error.
 */
@Repository
public class CatalogPersistence {

    private final JdbcTemplate jdbc;

    public CatalogPersistence(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── browse reads ────────────────────────────────────────────────────────

    /**
     * All categories with their item counts (0 included), alphabetical. LEFT JOIN so a freshly
     * added, still-empty category appears immediately in the browser.
     */
    public List<CategoryView> listCategories() {
        return jdbc.query(
                """
                SELECT c.category_id, c.category_name, COUNT(d.catalog_id) AS item_count
                FROM   item_categories c
                LEFT   JOIN digital_shopping_mart_catalog d ON d.category_id = c.category_id
                GROUP  BY c.category_id, c.category_name
                ORDER  BY c.category_name
                """,
                (rs, i) ->
                        new CategoryView(
                                rs.getInt("category_id"),
                                rs.getString("category_name"),
                                rs.getLong("item_count")));
    }

    /**
     * All subcategories under one category with their item counts (0 included), alphabetical. LEFT
     * JOIN so a freshly added, still-empty subcategory appears immediately.
     */
    public List<SubcategoryView> listSubcategories(int categoryId) {
        return jdbc.query(
                """
                SELECT s.subcategory_id, s.subcategory_name, COUNT(d.catalog_id) AS item_count
                FROM   item_subcategories s
                LEFT   JOIN digital_shopping_mart_catalog d ON d.sub_category_id = s.subcategory_id
                WHERE  s.category_id = ?
                GROUP  BY s.subcategory_id, s.subcategory_name
                ORDER  BY s.subcategory_name
                """,
                (rs, i) ->
                        new SubcategoryView(
                                rs.getInt("subcategory_id"),
                                rs.getString("subcategory_name"),
                                rs.getLong("item_count")),
                categoryId);
    }

    /** Catalog items under one subcategory, alphabetical by name. */
    public List<CatalogItemView> listItems(int subcategoryId) {
        return jdbc.query(
                """
                SELECT catalog_id, item_name
                FROM   digital_shopping_mart_catalog
                WHERE  sub_category_id = ?
                ORDER  BY item_name
                """,
                (rs, i) -> new CatalogItemView(rs.getInt("catalog_id"), rs.getString("item_name")),
                subcategoryId);
    }

    /**
     * The FULL taxonomy — every category with every subcategory, regardless of whether they hold
     * catalog items. This is the move-target picker's source, so a freshly added (still empty)
     * category or subcategory is immediately selectable. A category with no subcategories is kept
     * (LEFT JOIN) with an empty list.
     */
    public List<TaxonomyCategory> listTaxonomy() {
        Map<Integer, String> names = new LinkedHashMap<>();
        Map<Integer, List<TaxonomySubcategory>> subs = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT c.category_id, c.category_name, s.subcategory_id, s.subcategory_name
                FROM   item_categories c
                LEFT   JOIN item_subcategories s ON s.category_id = c.category_id
                ORDER  BY c.category_name, s.subcategory_name
                """,
                rs -> {
                    int categoryId = rs.getInt("category_id");
                    names.putIfAbsent(categoryId, rs.getString("category_name"));
                    List<TaxonomySubcategory> list =
                            subs.computeIfAbsent(categoryId, k -> new ArrayList<>());
                    int subId = rs.getInt("subcategory_id");
                    if (!rs.wasNull()) {
                        list.add(new TaxonomySubcategory(subId, rs.getString("subcategory_name")));
                    }
                });
        List<TaxonomyCategory> out = new ArrayList<>(names.size());
        names.forEach(
                (id, name) ->
                        out.add(new TaxonomyCategory(id, name, subs.getOrDefault(id, List.of()))));
        return out;
    }

    // ── lookups ─────────────────────────────────────────────────────────────

    public boolean categoryExists(int categoryId) {
        Integer n =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM item_categories WHERE category_id = ?",
                        Integer.class,
                        categoryId);
        return n != null && n > 0;
    }

    public boolean itemExists(int catalogId) {
        Integer n =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM digital_shopping_mart_catalog WHERE catalog_id = ?",
                        Integer.class,
                        catalogId);
        return n != null && n > 0;
    }

    /** The subcategory with its parent category resolved, or empty when the id is unknown. */
    public Optional<ResolvedSubcategory> resolveSubcategory(int subcategoryId) {
        return jdbc
                .query(
                        """
                        SELECT s.subcategory_id, s.subcategory_name, c.category_id, c.category_name
                        FROM   item_subcategories s
                        JOIN   item_categories    c ON c.category_id = s.category_id
                        WHERE  s.subcategory_id = ?
                        """,
                        (rs, i) ->
                                new ResolvedSubcategory(
                                        rs.getInt("subcategory_id"),
                                        rs.getString("subcategory_name"),
                                        rs.getInt("category_id"),
                                        rs.getString("category_name")),
                        subcategoryId)
                .stream()
                .findFirst();
    }

    // ── mutations ─────────────────────────────────────────────────────────────

    /** Inserts a category and returns it (item count 0). Throws {@code DuplicateKeyException}. */
    public CategoryView insertCategory(String name) {
        return jdbc.queryForObject(
                """
                INSERT INTO item_categories (category_name) VALUES (?)
                RETURNING category_id, category_name
                """,
                (rs, i) ->
                        new CategoryView(
                                rs.getInt("category_id"), rs.getString("category_name"), 0L),
                name);
    }

    /**
     * Inserts a subcategory under a category and returns it. Throws {@code DuplicateKeyException}.
     */
    public SubcategoryView insertSubcategory(int categoryId, String name) {
        return jdbc.queryForObject(
                """
                INSERT INTO item_subcategories (category_id, subcategory_name) VALUES (?, ?)
                RETURNING subcategory_id, subcategory_name
                """,
                (rs, i) ->
                        new SubcategoryView(
                                rs.getInt("subcategory_id"), rs.getString("subcategory_name"), 0L),
                categoryId,
                name);
    }

    /** Renames a category, returning the updated row, or empty when the id is unknown. */
    public Optional<CategoryRef> renameCategory(int categoryId, String name) {
        return jdbc
                .query(
                        """
                        UPDATE item_categories SET category_name = ? WHERE category_id = ?
                        RETURNING category_id, category_name
                        """,
                        (rs, i) ->
                                new CategoryRef(
                                        rs.getInt("category_id"), rs.getString("category_name")),
                        name,
                        categoryId)
                .stream()
                .findFirst();
    }

    /** Renames a subcategory, returning the updated row, or empty when the id is unknown. */
    public Optional<TaxonomySubcategory> renameSubcategory(int subcategoryId, String name) {
        return jdbc
                .query(
                        """
                        UPDATE item_subcategories SET subcategory_name = ? WHERE subcategory_id = ?
                        RETURNING subcategory_id, subcategory_name
                        """,
                        (rs, i) ->
                                new TaxonomySubcategory(
                                        rs.getInt("subcategory_id"),
                                        rs.getString("subcategory_name")),
                        name,
                        subcategoryId)
                .stream()
                .findFirst();
    }

    /**
     * Moves an item to a new subcategory, keeping its stored {@code category_id} consistent with
     * the subcategory's parent, and returns the item name.
     */
    public String updateItemSubcategory(int catalogId, int subcategoryId, int categoryId) {
        return jdbc.queryForObject(
                """
                UPDATE digital_shopping_mart_catalog
                SET    sub_category_id = ?, category_id = ?
                WHERE  catalog_id = ?
                RETURNING item_name
                """,
                String.class,
                subcategoryId,
                categoryId,
                catalogId);
    }
}
