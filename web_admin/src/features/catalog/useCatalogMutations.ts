import { useMutation, useQueryClient } from "@tanstack/react-query";

import {
  addCatalogCategory,
  addCatalogSubcategory,
  renameCatalogCategory,
  renameCatalogSubcategory,
  updateCatalogItemSubcategory,
} from "@/lib/api";

/**
 * Mutations for the catalog editor. Each invalidates exactly the query lists it
 * can change so the browser re-reads fresh counts/rows:
 *   - add category     → categories
 *   - add subcategory  → that category's subcategories
 *   - move item        → every catalog list (counts shift across subcategories)
 */

export function useAddCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => addCatalogCategory(name),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: ["catalog", "categories"] }),
  });
}

export function useAddSubcategory(categoryId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => addCatalogSubcategory(categoryId, name),
    onSuccess: () =>
      qc.invalidateQueries({
        queryKey: ["catalog", "subcategories", categoryId],
      }),
  });
}

export function useUpdateItemSubcategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { catalogId: number; subcategoryId: number }) =>
      updateCatalogItemSubcategory(vars.catalogId, vars.subcategoryId),
    // A move changes item membership + counts anywhere; refresh all catalog data.
    onSuccess: () => qc.invalidateQueries({ queryKey: ["catalog"] }),
  });
}

export function useRenameCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { categoryId: number; name: string }) =>
      renameCatalogCategory(vars.categoryId, vars.name),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["catalog"] }),
  });
}

export function useRenameSubcategory(categoryId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { subcategoryId: number; name: string }) =>
      renameCatalogSubcategory(vars.subcategoryId, vars.name),
    onSuccess: () => {
      qc.invalidateQueries({
        queryKey: ["catalog", "subcategories", categoryId],
      });
      qc.invalidateQueries({ queryKey: ["catalog", "taxonomy"] });
    },
  });
}
