import { useQuery } from "@tanstack/react-query";

import {
  fetchCatalogCategories,
  fetchCatalogItems,
  fetchCatalogSubcategories,
  fetchCatalogTaxonomy,
} from "@/lib/api";

/**
 * Query hooks for the catalog drill-down. Each level is enabled only once its
 * parent id is known, so navigating down fires exactly one request per level
 * and results stay cached when the user steps back up.
 */

export function useCatalogCategories() {
  return useQuery({
    queryKey: ["catalog", "categories"],
    queryFn: fetchCatalogCategories,
  });
}

export function useCatalogSubcategories(categoryId: number | null) {
  return useQuery({
    queryKey: ["catalog", "subcategories", categoryId],
    queryFn: () => fetchCatalogSubcategories(categoryId!),
    enabled: categoryId !== null,
  });
}

export function useCatalogItems(subcategoryId: number | null) {
  return useQuery({
    queryKey: ["catalog", "items", subcategoryId],
    queryFn: () => fetchCatalogItems(subcategoryId!),
    enabled: subcategoryId !== null,
  });
}

export function useCatalogTaxonomy() {
  return useQuery({
    queryKey: ["catalog", "taxonomy"],
    queryFn: fetchCatalogTaxonomy,
  });
}
