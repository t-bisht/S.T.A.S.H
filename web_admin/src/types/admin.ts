/**
 * Admin API DTOs. The admin module is reduced to its login flow, so only the
 * auth-surface shapes remain.
 *
 * Field names are camelCase: Jackson's default for Java records, matching
 * `web/src/types/auth.ts`.
 */

/** Session identity returned by GET /admin/api/me. */
export type AdminMe = { username: string };

/** Flat body — matches user_engine's AuthErrorAdvice.ErrorBody. Not nested under `error`. */
export type ApiError = { code: ErrorCode; message: string };
export type ErrorCode = "unauthorized" | "validation_error" | "internal_error";

// --- Catalog browser (read-only drill-down) -------------------------------

/** A main category holding catalog items, with its item count. */
export type CatalogCategory = {
  categoryId: number;
  categoryName: string;
  itemCount: number;
};

/** A leaf subcategory within a category, with its item count. */
export type CatalogSubcategory = {
  subcategoryId: number;
  subcategoryName: string;
  itemCount: number;
};

/** One product row from digital_shopping_mart_catalog. */
export type CatalogItem = {
  catalogId: number;
  itemName: string;
};

/** Result of moving an item to another subcategory (PATCH /items/{id}). */
export type ItemUpdateResult = {
  catalogId: number;
  itemName: string;
  subcategoryId: number;
  subcategoryName: string;
  categoryId: number;
  categoryName: string;
};

/** A selectable subcategory in the move picker (no item count). */
export type TaxonomySubcategory = {
  subcategoryId: number;
  subcategoryName: string;
};

/** A category with ALL its subcategories — the move picker's data. */
export type TaxonomyCategory = {
  categoryId: number;
  categoryName: string;
  subcategories: TaxonomySubcategory[];
};
