import { useEffect, useMemo, useState } from "react";
import { Loader2, X } from "lucide-react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { AdminApiError } from "@/lib/api";

import { useCatalogTaxonomy } from "./useCatalog";
import { useUpdateItemSubcategory } from "./useCatalogMutations";

/**
 * Modal for reclassifying one catalog item. Shows every category and its
 * subcategories (the full taxonomy, including empty ones), seeded to the item's
 * current placement. Picking a category narrows the subcategory select; Save
 * PATCHes the item with the chosen subcategory id (the server derives the
 * category from it). Validation messages surface inline.
 */
export function MoveItemModal({
  catalogId,
  itemName,
  currentCategoryId,
  currentSubcategoryId,
  onClose,
  onSaved,
}: {
  catalogId: number;
  itemName: string;
  currentCategoryId: number;
  currentSubcategoryId: number;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { data: taxonomy, isLoading, isError } = useCatalogTaxonomy();
  const updateMutation = useUpdateItemSubcategory();

  const [categoryId, setCategoryId] = useState<number>(currentCategoryId);
  const [subcategoryId, setSubcategoryId] = useState<number>(
    currentSubcategoryId,
  );
  const [error, setError] = useState<string | null>(null);

  // Close on Escape.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const subcategories = useMemo(
    () =>
      taxonomy?.find((c) => c.categoryId === categoryId)?.subcategories ?? [],
    [taxonomy, categoryId],
  );

  function pickCategory(id: number) {
    setCategoryId(id);
    setError(null);
    // Reset the subcategory to the first option of the newly chosen category.
    const first = taxonomy?.find((c) => c.categoryId === id)?.subcategories[0];
    setSubcategoryId(first ? first.subcategoryId : -1);
  }

  function save() {
    setError(null);
    if (subcategoryId < 0) {
      setError("This category has no subcategories — pick another.");
      return;
    }
    if (subcategoryId === currentSubcategoryId) {
      setError("Pick a different subcategory.");
      return;
    }
    updateMutation.mutate(
      { catalogId, subcategoryId },
      {
        onSuccess: () => onSaved(),
        onError: (e) =>
          setError(
            e instanceof AdminApiError && e.message
              ? e.message
              : "Could not update the item.",
          ),
      },
    );
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-surface-900/40 px-4"
      role="dialog"
      aria-modal="true"
      aria-label="Change item category and subcategory"
      onClick={onClose}
    >
      <div
        className="w-full max-w-md rounded-lg border border-surface-200 bg-surface-0 shadow-lg"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-3 border-b border-surface-100 px-5 py-4">
          <div>
            <h2 className="text-sm font-semibold text-surface-900">
              Change category &amp; subcategory
            </h2>
            <p className="mt-0.5 text-sm text-surface-500">{itemName}</p>
          </div>
          <button
            type="button"
            aria-label="Close"
            onClick={onClose}
            className="text-surface-400 hover:text-surface-900"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        <div className="flex flex-col gap-4 px-5 py-4">
          {isLoading ? (
            <div className="flex justify-center py-6 text-surface-500">
              <Loader2
                className="h-6 w-6 animate-spin text-brand-600"
                aria-hidden="true"
              />
            </div>
          ) : isError || !taxonomy ? (
            <Alert variant="destructive">
              <AlertDescription>Could not load the taxonomy.</AlertDescription>
            </Alert>
          ) : (
            <>
              <label className="flex flex-col gap-1.5 text-sm font-medium text-surface-700">
                Category
                <select
                  value={categoryId}
                  disabled={updateMutation.isPending}
                  onChange={(e) => pickCategory(Number(e.target.value))}
                  className="h-10 rounded-md border border-surface-200 bg-surface-0 px-3 text-sm text-surface-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 disabled:opacity-50"
                >
                  {taxonomy.map((c) => (
                    <option key={c.categoryId} value={c.categoryId}>
                      {c.categoryName}
                    </option>
                  ))}
                </select>
              </label>

              <label className="flex flex-col gap-1.5 text-sm font-medium text-surface-700">
                Subcategory
                <select
                  value={subcategoryId}
                  disabled={updateMutation.isPending || subcategories.length === 0}
                  onChange={(e) => {
                    setSubcategoryId(Number(e.target.value));
                    setError(null);
                  }}
                  className="h-10 rounded-md border border-surface-200 bg-surface-0 px-3 text-sm text-surface-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 disabled:opacity-50"
                >
                  {subcategories.length === 0 ? (
                    <option value={-1}>No subcategories</option>
                  ) : (
                    subcategories.map((s) => (
                      <option key={s.subcategoryId} value={s.subcategoryId}>
                        {s.subcategoryName}
                      </option>
                    ))
                  )}
                </select>
              </label>

              {error && (
                <Alert variant="destructive">
                  <AlertDescription>{error}</AlertDescription>
                </Alert>
              )}
            </>
          )}
        </div>

        <div className="flex justify-end gap-2 border-t border-surface-100 px-5 py-4">
          <Button
            variant="ghost"
            size="sm"
            onClick={onClose}
            disabled={updateMutation.isPending}
          >
            Cancel
          </Button>
          <Button
            size="sm"
            onClick={save}
            disabled={updateMutation.isPending || isLoading || isError}
          >
            {updateMutation.isPending && (
              <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
            )}
            Save
          </Button>
        </div>
      </div>
    </div>
  );
}
