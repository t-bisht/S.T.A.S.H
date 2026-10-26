import type {
  CatalogCategory,
  CatalogItem,
  CatalogSubcategory,
  ItemUpdateResult,
  TaxonomyCategory,
} from "@/types/admin";

/**
 * Admin API client. Three jobs:
 *   1. All paths are RELATIVE — no `window.ENV`/base-URL prefix. The UI is
 *      same-origin with the API in production (admin_engine serves the bundle)
 *      and proxied in dev (Vite `/admin` → 127.0.0.1:7084).
 *   2. Attach `credentials: 'include'` so STASH_ADMIN_SESSION rides along.
 *   3. Hard-redirect on 401 for every path EXCEPT `/admin/api/me`.
 */

/** Login page with the "your session expired" banner. */
export const ADMIN_SESSION_EXPIRED_PATH = "/login?error=session_expired";

/** The one path whose 401 is NOT a session-expiry signal. */
const ME_PATH = "/admin/api/me";

export class AdminApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string | null,
  ) {
    super(message);
    this.name = "AdminApiError";
  }
}

export async function apiFetch(
  path: string,
  init: RequestInit = {},
): Promise<Response> {
  const res = await fetch(path, {
    ...init,
    credentials: "include",
    headers: { "Content-Type": "application/json", ...init.headers },
  });

  // Session expired or engine restarted mid-session. `/admin/api/me` is excluded:
  // RequireAdminAuth consumes its 401 as the normal "not logged in" signal.
  if (res.status === 401 && !path.endsWith(ME_PATH)) {
    // Hard reload — clears the TanStack Query cache cleanly.
    window.location.href = ADMIN_SESSION_EXPIRED_PATH;
  }

  return res;
}

/** Parse the flat `{code, message}` error body into an AdminApiError. */
async function parseError(res: Response): Promise<AdminApiError> {
  let code: string | null = null;
  let message = `Request failed with ${res.status}`;
  try {
    const body = (await res.json()) as { code?: unknown; message?: unknown };
    if (body && typeof body === "object") {
      if (typeof body.code === "string") code = body.code;
      if (typeof body.message === "string") message = body.message;
    }
  } catch {
    // Non-JSON body (e.g. a bare 500) — keep the generic message.
  }
  return new AdminApiError(message, res.status, code);
}

// ---------------------------------------------------------------------------
// Auth endpoints — the only surface the admin module exposes.
// ---------------------------------------------------------------------------

/**
 * POST /admin/login. Deliberately uses a direct `fetch`, NOT `apiFetch`: a 401
 * here means "bad credentials" — an inline form error — not an expired session.
 * Routing it through `apiFetch` would hard-redirect to the session-expired
 * banner and the inline error would never render.
 */
export async function login(username: string, password: string): Promise<void> {
  const res = await fetch("/admin/login", {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  if (res.status === 204) return;
  throw await parseError(res);
}

export async function logout(): Promise<void> {
  const res = await apiFetch("/admin/logout", { method: "POST" });
  if (res.status === 204 || res.ok) return;
  throw await parseError(res);
}

/** Returns the session identity, or null on 401 (not signed in). */
export async function fetchMe(): Promise<{ username: string } | null> {
  const res = await apiFetch(ME_PATH);
  if (res.status === 401) return null;
  if (!res.ok) throw await parseError(res);
  return (await res.json()) as { username: string };
}

// ---------------------------------------------------------------------------
// Catalog browser — read-only drill-down (categories → subcategories → items).
// ---------------------------------------------------------------------------

export async function fetchCatalogCategories(): Promise<CatalogCategory[]> {
  const res = await apiFetch("/admin/api/catalog/categories");
  if (!res.ok) throw await parseError(res);
  return (await res.json()) as CatalogCategory[];
}

export async function fetchCatalogSubcategories(
  categoryId: number,
): Promise<CatalogSubcategory[]> {
  const res = await apiFetch(
    `/admin/api/catalog/categories/${categoryId}/subcategories`,
  );
  if (!res.ok) throw await parseError(res);
  return (await res.json()) as CatalogSubcategory[];
}

export async function fetchCatalogItems(
  subcategoryId: number,
): Promise<CatalogItem[]> {
  const res = await apiFetch(
    `/admin/api/catalog/subcategories/${subcategoryId}/items`,
  );
  if (!res.ok) throw await parseError(res);
  return (await res.json()) as CatalogItem[];
}

export async function addCatalogCategory(
  categoryName: string,
): Promise<CatalogCategory> {
  const res = await apiFetch("/admin/api/catalog/categories", {
    method: "POST",
    body: JSON.stringify({ categoryName }),
  });
  if (!res.ok) throw await parseError(res);
  return (await res.json()) as CatalogCategory;
}

export async function addCatalogSubcategory(
  categoryId: number,
  subcategoryName: string,
): Promise<CatalogSubcategory> {
  const res = await apiFetch(
    `/admin/api/catalog/categories/${categoryId}/subcategories`,
    { method: "POST", body: JSON.stringify({ subcategoryName }) },
  );
  if (!res.ok) throw await parseError(res);
  return (await res.json()) as CatalogSubcategory;
}

export async function updateCatalogItemSubcategory(
  catalogId: number,
  subcategoryId: number,
): Promise<ItemUpdateResult> {
  const res = await apiFetch(`/admin/api/catalog/items/${catalogId}`, {
    method: "PATCH",
    body: JSON.stringify({ subcategoryId }),
  });
  if (!res.ok) throw await parseError(res);
  return (await res.json()) as ItemUpdateResult;
}

export async function fetchCatalogTaxonomy(): Promise<TaxonomyCategory[]> {
  const res = await apiFetch("/admin/api/catalog/taxonomy");
  if (!res.ok) throw await parseError(res);
  return (await res.json()) as TaxonomyCategory[];
}

export async function renameCatalogCategory(
  categoryId: number,
  categoryName: string,
): Promise<CatalogCategory> {
  const res = await apiFetch(`/admin/api/catalog/categories/${categoryId}`, {
    method: "PATCH",
    body: JSON.stringify({ categoryName }),
  });
  if (!res.ok) throw await parseError(res);
  return (await res.json()) as CatalogCategory;
}

export async function renameCatalogSubcategory(
  subcategoryId: number,
  subcategoryName: string,
): Promise<CatalogSubcategory> {
  const res = await apiFetch(
    `/admin/api/catalog/subcategories/${subcategoryId}`,
    { method: "PATCH", body: JSON.stringify({ subcategoryName }) },
  );
  if (!res.ok) throw await parseError(res);
  return (await res.json()) as CatalogSubcategory;
}
