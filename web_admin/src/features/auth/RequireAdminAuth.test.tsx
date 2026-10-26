import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { fetchMe } from "@/lib/api";

import { RequireAdminAuth } from "./RequireAdminAuth";

vi.mock("@/lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  fetchMe: vi.fn(),
}));

const mockFetchMe = vi.mocked(fetchMe);

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

function renderGuarded() {
  return render(
    <QueryClientProvider client={makeClient()}>
      <MemoryRouter initialEntries={["/dashboard"]}>
        <Routes>
          <Route path="/login" element={<div data-testid="login">LOGIN</div>} />
          <Route element={<RequireAdminAuth />}>
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

describe("RequireAdminAuth", () => {
  beforeEach(() => {
    mockFetchMe.mockReset();
  });

  it("renders the protected route when a session exists", async () => {
    mockFetchMe.mockResolvedValueOnce({ username: "admin" });
    renderGuarded();

    expect(await screen.findByTestId("dashboard")).toBeInTheDocument();
  });

  it("redirects to /login when the session probe returns null (401)", async () => {
    mockFetchMe.mockResolvedValueOnce(null);
    renderGuarded();

    expect(await screen.findByTestId("login")).toBeInTheDocument();
    expect(screen.queryByTestId("dashboard")).not.toBeInTheDocument();
  });
});
