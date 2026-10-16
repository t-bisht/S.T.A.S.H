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

  it("prefixes the request path with window.ENV.API_BASE_URL", async () => {
    const mock = vi
      .mocked(global.fetch)
      .mockResolvedValueOnce(new Response(null, { status: 200 }));

    await apiFetch("/auth/session", { method: "POST" });

    const [url, init] = mock.mock.calls[0]!;
    expect(url).toBe("/api/auth/session");
    expect(init?.credentials).toBe("include");
    expect((init?.headers as Record<string, string>)["Content-Type"]).toBe(
      "application/json",
    );
  });

  it("hard-redirects to '/' on 401 for /auth/* paths", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(
      new Response(null, { status: 401 }),
    );

    await apiFetch("/auth/session");

    expect(window.location.href).toBe("/");
  });

  it("hard-redirects to the signed-out banner on 401 for any other path", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(
      new Response(null, { status: 401 }),
    );

    await apiFetch("/receipts");

    expect(window.location.href).toBe("/?error=signed_out");
  });

  it("does not redirect on non-401 responses", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(
      new Response(null, { status: 500 }),
    );

    await apiFetch("/receipts");

    expect(window.location.href).toBe("");
  });

  it("does NOT redirect on 401 for /me", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(
      new Response(null, { status: 401 }),
    );

    await apiFetch("/me");

    expect(window.location.href).toBe("");
  });
});
