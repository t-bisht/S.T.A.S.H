import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router";
import { ChevronLeft, ChevronRight, Loader2, Pencil, Plus } from "lucide-react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { AdminApiError } from "@/lib/api";

import {
  useCatalogCategories,
  useCatalogItems,
  useCatalogSubcategories,
} from "./useCatalog";
import {
  useAddCategory,
  useAddSubcategory,
  useRenameCategory,
  useRenameSubcategory,
} from "./useCatalogMutations";
import { MoveItemModal } from "./MoveItemModal";

/**
 * Digital Shopping Mart catalog editor: a three-level drill-down
 * (categories → subcategories → items) where each level can also mutate:
 *   - categories level: add a category
 *   - subcategories level: add a subcategory under the chosen category
 *   - items level: move an item to another subcategory
 *
 * Selection is held as two ids; the deepest non-null id picks the level.
 */

interface Sel {
  id: number;
  name: string;
}

function errMessage(e: unknown, fallback: string): string {
  return e instanceof AdminApiError && e.message ? e.message : fallback;
}

export function CatalogPage() {
  const navigate = useNavigate();
  const [category, setCategory] = useState<Sel | null>(null);
  const [subcategory, setSubcategory] = useState<Sel | null>(null);

  const level: "categories" | "subcategories" | "items" =
    subcategory !== null
      ? "items"
      : category !== null
        ? "subcategories"
        : "categories";

  const crumbs: { label: string; onClick?: () => void }[] = [
    {
      label: "Categories",
      onClick: () => {
        setCategory(null);
        setSubcategory(null);
      },
    },
  ];
  if (category)
    crumbs.push({ label: category.name, onClick: () => setSubcategory(null) });
  if (subcategory) crumbs.push({ label: subcategory.name });

  function goBack() {
    if (subcategory) setSubcategory(null);
    else if (category) setCategory(null);
    else navigate("/dashboard");
  }

  return (
    <div className="min-h-screen bg-surface-50 px-4 py-8">
      <div className="mx-auto w-full max-w-2xl">
        <Card>
          <CardHeader>
            <CardTitle>Manage Digital Shopping Mart Catalog</CardTitle>
            <CardDescription>
              Browse by category, then subcategory, down to items — and edit as
              you go.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <nav
              aria-label="Breadcrumb"
              className="flex flex-wrap items-center gap-1 text-sm text-surface-500"
            >
              {crumbs.map((c, i) => {
                const last = i === crumbs.length - 1;
                return (
                  <span key={i} className="flex items-center gap-1">
                    {i > 0 && (
                      <ChevronRight
                        className="h-3.5 w-3.5 text-surface-300"
                        aria-hidden="true"
                      />
                    )}
                    {last || !c.onClick ? (
                      <span className="font-medium text-surface-900">
                        {c.label}
                      </span>
                    ) : (
                      <button
                        type="button"
                        onClick={c.onClick}
                        className="hover:text-surface-900 hover:underline"
                      >
                        {c.label}
                      </button>
                    )}
                  </span>
                );
              })}
            </nav>

            <Button
              variant="ghost"
              size="sm"
              onClick={goBack}
              className="w-fit px-2"
            >
              <ChevronLeft className="h-4 w-4" aria-hidden="true" />
              Back
            </Button>

            {level === "categories" && (
              <CategoryList onSelect={(id, name) => setCategory({ id, name })} />
            )}
            {level === "subcategories" && category && (
              <SubcategoryList
                categoryId={category.id}
                onSelect={(id, name) => setSubcategory({ id, name })}
              />
            )}
            {level === "items" && category && subcategory && (
              <ItemList categoryId={category.id} subcategoryId={subcategory.id} />
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

// --- shared presentational bits ------------------------------------------

function Spinner() {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex items-center justify-center py-8 text-surface-500"
    >
      <Loader2 className="h-6 w-6 animate-spin text-brand-600" aria-hidden="true" />
    </div>
  );
}

function LoadError({ message }: { message: string }) {
  return (
    <Alert variant="destructive">
      <AlertDescription>{message}</AlertDescription>
    </Alert>
  );
}

function EmptyRow({ label }: { label: string }) {
  return <p className="py-6 text-center text-sm text-surface-500">{label}</p>;
}

/**
 * A catalog row that drills down on click and can be renamed in place. The
 * pencil toggles an inline editor; saving calls `onRename` and the parent
 * reports success/failure back through the callbacks so the row shows its own
 * pending spinner and validation message.
 */
function RenamableRow({
  label,
  count,
  onOpen,
  onRename,
}: {
  label: string;
  count?: number;
  onOpen: () => void;
  onRename: (
    name: string,
    cb: { onSuccess: () => void; onError: (message: string) => void },
  ) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(label);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function startEdit() {
    setValue(label);
    setError(null);
    setEditing(true);
  }

  function save() {
    const trimmed = value.trim();
    if (!trimmed) {
      setError("Name is required.");
      return;
    }
    if (trimmed === label) {
      setEditing(false);
      return;
    }
    setError(null);
    setPending(true);
    onRename(trimmed, {
      onSuccess: () => {
        setPending(false);
        setEditing(false);
      },
      onError: (message) => {
        setPending(false);
        setError(message);
      },
    });
  }

  if (editing) {
    return (
      <div className="flex flex-col gap-2 rounded-md border border-surface-200 bg-surface-0 px-4 py-3">
        <div className="flex gap-2">
          <input
            type="text"
            value={value}
            autoFocus
            disabled={pending}
            onChange={(e) => setValue(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") save();
              if (e.key === "Escape") setEditing(false);
            }}
            className="h-9 flex-1 rounded-md border border-surface-200 bg-surface-0 px-3 text-sm text-surface-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 disabled:opacity-50"
          />
          <Button size="sm" onClick={save} disabled={pending}>
            {pending && (
              <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
            )}
            Save
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setEditing(false)}
            disabled={pending}
          >
            Cancel
          </Button>
        </div>
        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
      </div>
    );
  }

  return (
    <div className="flex items-stretch gap-2">
      <button
        type="button"
        onClick={onOpen}
        className="flex flex-1 items-center justify-between rounded-md border border-surface-200 bg-surface-0 px-4 py-3 text-left text-sm text-surface-900 transition-colors hover:bg-surface-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
      >
        <span>{label}</span>
        <span className="flex items-center gap-2 text-surface-500">
          {count !== undefined && (
            <span className="rounded-full bg-surface-100 px-2 py-0.5 text-xs">
              {count}
            </span>
          )}
          <ChevronRight className="h-4 w-4" aria-hidden="true" />
        </span>
      </button>
      <Button
        variant="outline"
        size="sm"
        aria-label={`Rename ${label}`}
        onClick={startEdit}
        className="px-3"
      >
        <Pencil className="h-4 w-4" aria-hidden="true" />
      </Button>
    </div>
  );
}

/** Inline "add X" form: single text input + add button, with an error slot. */
function InlineAddForm({
  placeholder,
  submitLabel,
  pending,
  error,
  onSubmit,
}: {
  placeholder: string;
  submitLabel: string;
  pending: boolean;
  error: string | null;
  onSubmit: (value: string) => void;
}) {
  const [value, setValue] = useState("");

  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const trimmed = value.trim();
    if (!trimmed) return;
    onSubmit(trimmed);
  }

  return (
    <form onSubmit={handle} className="flex flex-col gap-2">
      <div className="flex gap-2">
        <input
          type="text"
          value={value}
          placeholder={placeholder}
          disabled={pending}
          onChange={(e) => setValue(e.target.value)}
          className="h-10 flex-1 rounded-md border border-surface-200 bg-surface-0 px-3 text-sm text-surface-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 disabled:opacity-50"
        />
        <Button type="submit" size="sm" disabled={pending || !value.trim()}>
          {pending ? (
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
          ) : (
            <Plus className="h-4 w-4" aria-hidden="true" />
          )}
          {submitLabel}
        </Button>
      </div>
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
    </form>
  );
}

// --- level views ----------------------------------------------------------

function CategoryList({
  onSelect,
}: {
  onSelect: (id: number, name: string) => void;
}) {
  const { data, isLoading, isError } = useCatalogCategories();
  const addMutation = useAddCategory();
  const renameMutation = useRenameCategory();
  const [error, setError] = useState<string | null>(null);

  function add(name: string) {
    setError(null);
    addMutation.mutate(name, {
      onError: (e) => setError(errMessage(e, "Could not add category.")),
    });
  }

  return (
    <div className="flex flex-col gap-4">
      <InlineAddForm
        placeholder="New category name"
        submitLabel="Add category"
        pending={addMutation.isPending}
        error={error}
        onSubmit={add}
      />
      {isLoading ? (
        <Spinner />
      ) : isError ? (
        <LoadError message="Could not load categories." />
      ) : !data || data.length === 0 ? (
        <EmptyRow label="No categories yet." />
      ) : (
        <div className="flex flex-col gap-2">
          {data.map((c) => (
            <RenamableRow
              key={c.categoryId}
              label={c.categoryName}
              count={c.itemCount}
              onOpen={() => onSelect(c.categoryId, c.categoryName)}
              onRename={(name, cb) =>
                renameMutation.mutate(
                  { categoryId: c.categoryId, name },
                  {
                    onSuccess: () => cb.onSuccess(),
                    onError: (e) =>
                      cb.onError(errMessage(e, "Could not rename category.")),
                  },
                )
              }
            />
          ))}
        </div>
      )}
    </div>
  );
}

function SubcategoryList({
  categoryId,
  onSelect,
}: {
  categoryId: number;
  onSelect: (id: number, name: string) => void;
}) {
  const { data, isLoading, isError } = useCatalogSubcategories(categoryId);
  const addMutation = useAddSubcategory(categoryId);
  const renameMutation = useRenameSubcategory(categoryId);
  const [error, setError] = useState<string | null>(null);

  function add(name: string) {
    setError(null);
    addMutation.mutate(name, {
      onError: (e) => setError(errMessage(e, "Could not add subcategory.")),
    });
  }

  return (
    <div className="flex flex-col gap-4">
      <InlineAddForm
        placeholder="New subcategory name"
        submitLabel="Add subcategory"
        pending={addMutation.isPending}
        error={error}
        onSubmit={add}
      />
      {isLoading ? (
        <Spinner />
      ) : isError ? (
        <LoadError message="Could not load subcategories." />
      ) : !data || data.length === 0 ? (
        <EmptyRow label="No subcategories yet." />
      ) : (
        <div className="flex flex-col gap-2">
          {data.map((s) => (
            <RenamableRow
              key={s.subcategoryId}
              label={s.subcategoryName}
              count={s.itemCount}
              onOpen={() => onSelect(s.subcategoryId, s.subcategoryName)}
              onRename={(name, cb) =>
                renameMutation.mutate(
                  { subcategoryId: s.subcategoryId, name },
                  {
                    onSuccess: () => cb.onSuccess(),
                    onError: (e) =>
                      cb.onError(errMessage(e, "Could not rename subcategory.")),
                  },
                )
              }
            />
          ))}
        </div>
      )}
    </div>
  );
}

function ItemList({
  categoryId,
  subcategoryId,
}: {
  categoryId: number;
  subcategoryId: number;
}) {
  const { data, isLoading, isError } = useCatalogItems(subcategoryId);

  if (isLoading) return <Spinner />;
  if (isError) return <LoadError message="Could not load items." />;
  if (!data || data.length === 0) return <EmptyRow label="No items." />;

  return (
    <ul className="flex flex-col gap-2">
      {data.map((it) => (
        <ItemRow
          key={it.catalogId}
          catalogId={it.catalogId}
          itemName={it.itemName}
          currentCategoryId={categoryId}
          currentSubcategoryId={subcategoryId}
        />
      ))}
    </ul>
  );
}

function ItemRow({
  catalogId,
  itemName,
  currentCategoryId,
  currentSubcategoryId,
}: {
  catalogId: number;
  itemName: string;
  currentCategoryId: number;
  currentSubcategoryId: number;
}) {
  const [open, setOpen] = useState(false);

  return (
    <li className="rounded-md border border-surface-200 bg-surface-0 px-4 py-3 text-sm text-surface-900">
      <div className="flex items-center justify-between gap-3">
        <span>{itemName}</span>
        <Button variant="outline" size="sm" onClick={() => setOpen(true)}>
          Change category / subcategory
        </Button>
      </div>

      {open && (
        <MoveItemModal
          catalogId={catalogId}
          itemName={itemName}
          currentCategoryId={currentCategoryId}
          currentSubcategoryId={currentSubcategoryId}
          onClose={() => setOpen(false)}
          onSaved={() => setOpen(false)}
        />
      )}
    </li>
  );
}
