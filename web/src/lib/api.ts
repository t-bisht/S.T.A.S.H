/**
 * Thin fetch wrapper. Three jobs:
 *   1. Prefix every call with window.ENV.API_BASE_URL (same-origin via proxy).
 *   2. Attach `credentials: 'include'` so STASH_SESSION rides along.
 *   3. Hard-redirect on 401 (see `redirectFor401`).
 */

/** Login page with the "you've been signed out" banner. */
export const SIGNED_OUT_PATH = "/?error=signed_out";

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly response: Response,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/**
 * Where to send the browser when `path` answers 401, or `null` to stay put.
 *   - `/me`      → stay: 401 is the normal "not logged in" signal, consumed
 *                  by RequireAuth / LandingPage.
 *   - `/auth/*`  → `/`: login/logout plumbing; no banner.
 *   - otherwise  → signed-out banner: the user had a session that the server
 *                  no longer accepts (expired or force-logged-out).
 */
export function redirectFor401(path: string): string | null {
  if (path.startsWith("/me")) return null;
  if (path.startsWith("/auth/")) return "/";
  return SIGNED_OUT_PATH;
}

export async function apiFetch(
  path: string,
  init: RequestInit = {},
): Promise<Response> {
  const base = window.ENV.API_BASE_URL;
  const headers: HeadersInit = {
    "Content-Type": "application/json",
    ...init.headers,
  };

  const res = await fetch(`${base}${path}`, {
    ...init,
    credentials: "include",
    headers,
  });

  if (res.status === 401) {
    const target = redirectFor401(path);
    // Hard reload — clears any stale TanStack Query cache cleanly and lands
    // the user on the login page (LandingPage at '/').
    if (target !== null) window.location.href = target;
  }

  return res;
}
