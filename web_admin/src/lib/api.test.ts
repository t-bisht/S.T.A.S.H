import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { apiFetch } from "./api";

describe("apiFetch", () => {
  const originalFetch = global.fetch;
  const originalLocation = window.location;

  beforeEach(() => {
    global.fetch = vi.fn();
    Object.defineProperty(window, "location", {
      value: { href: "" },
      writable: true,
      configurable: true,
    });
  });

  afterEach(() => {
    global.fetch = originalFetch;
    Object.defineProperty(window, "location", {
      value: originalLocation,
      writable: true,
      configurable: true,
    });
    vi.restoreAllMocks();
  });

  it("uses the relative path verbatim (no base-URL prefix) with credentials", async () => {
    const mock = vi
      .mocked(global.fetch)
      .mockResolvedValueOnce(new Response(null, { status: 200 }));

    await apiFetch("/admin/api/me");

    const [url, init] = mock.mock.calls[0]!;
    expect(url).toBe("/admin/api/me");
    expect(init?.credentials).toBe("include");
    expect((init?.headers as Record<string, string>)["Content-Type"]).toBe(
      "application/json",
    );
  });

  it("hard-redirects to the session-expired banner on 401 for non-me paths", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(
      new Response(null, { status: 401 }),
    );

    await apiFetch("/admin/logout");

    expect(window.location.href).toBe("/login?error=session_expired");
  });

  it("does NOT redirect on 401 for /admin/api/me", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(
      new Response(null, { status: 401 }),
    );

    await apiFetch("/admin/api/me");

    expect(window.location.href).toBe("");
  });

  it("does not redirect on non-401 responses", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(
      new Response(null, { status: 500 }),
    );

    await apiFetch("/admin/logout");

    expect(window.location.href).toBe("");
  });
});
