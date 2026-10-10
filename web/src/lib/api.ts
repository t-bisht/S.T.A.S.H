/**
 * Thin fetch wrapper. Three jobs:
 *   1. Prefix every call with window.ENV.API_BASE_URL (same-origin via proxy).
 *   2. Attach `credentials: 'include'` so STASH_SESSION rides along.
 *   3. Hard-redirect to '/' on 401, except for '/me' — a 401 there is a normal
 *      "not logged in" signal (consumed by RequireAuth, not a system failure).
 */

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

  if (res.status === 401 && !path.startsWith("/me")) {
    // Hard reload — clears any stale TanStack Query cache cleanly and lands
    // the user on the login page (LandingPage at '/').
    window.location.href = "/";
  }

  return res;
}
