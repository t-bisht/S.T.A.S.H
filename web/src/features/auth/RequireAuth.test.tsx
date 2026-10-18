import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiFetch } from "@/lib/api";

import { RequireAuth } from "./RequireAuth";
import { clearDeliberateSignOut, markDeliberateSignOut } from "./signOutIntent";
import { CURRENT_USER_KEY } from "./useCurrentUser";

vi.mock("@/lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  apiFetch: vi.fn(),
}));

const mockApiFetch = vi.mocked(apiFetch);

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

/** Renders the landing route's full URL so tests can assert the query string. */
function Landing() {
  const location = useLocation();
  return <div data-testid="landing">{location.pathname + location.search}</div>;
}

function renderAt(client: QueryClient, path: string) {
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/" element={<Landing />} />
          <Route element={<RequireAuth />}>
            <Route
              path="/dashboard"
              element={<div data-testid="dashboard">DASHBOARD</div>}
            />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function unauthenticated(): Response {
  return new Response(null, { status: 401 });
}

function authenticated(): Response {
  return new Response(
    JSON.stringify({
      userId: "u1",
      email: "u@example.com",
      displayName: "User One",
      picture: null,
    }),
    { status: 200, headers: { "Content-Type": "application/json" } },
  );
}

describe("RequireAuth", () => {
  beforeEach(() => {
    mockApiFetch.mockReset();
    clearDeliberateSignOut();
  });

  it("renders the protected route when signed in", async () => {
    mockApiFetch.mockResolvedValueOnce(authenticated());
    renderAt(makeClient(), "/dashboard");

    expect(await screen.findByTestId("dashboard")).toBeInTheDocument();
  });

  it("sends a never-signed-in visitor to '/' without a banner", async () => {
    mockApiFetch.mockResolvedValueOnce(unauthenticated());
    renderAt(makeClient(), "/dashboard");

    expect((await screen.findByTestId("landing")).textContent).toBe("/");
  });

  it("sends a user whose session vanished to the signed-out banner", async () => {
    const client = makeClient();
    mockApiFetch.mockResolvedValueOnce(authenticated());
    renderAt(client, "/dashboard");
    await screen.findByTestId("dashboard");

    mockApiFetch.mockResolvedValueOnce(unauthenticated());
    await act(() => client.refetchQueries({ queryKey: CURRENT_USER_KEY }));

    expect((await screen.findByTestId("landing")).textContent).toBe(
      "/?error=signed_out",
    );
  });

  it("sends a user who clicked sign out to '/' without a banner", async () => {
    const client = makeClient();
    mockApiFetch.mockResolvedValueOnce(authenticated());
    renderAt(client, "/dashboard");
    await screen.findByTestId("dashboard");

    markDeliberateSignOut();
    act(() => client.setQueryData(CURRENT_USER_KEY, null));

    expect((await screen.findByTestId("landing")).textContent).toBe("/");
  });
});
