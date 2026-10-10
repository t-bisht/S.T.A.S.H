import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { Dashboard } from "@/features/dashboard/Dashboard";
import { apiFetch } from "@/lib/api";

import { LandingPage } from "./LandingPage";

vi.mock("@/lib/api", () => ({
  apiFetch: vi.fn(),
}));

const mockApiFetch = vi.mocked(apiFetch);

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

function renderAt(path: string) {
  return render(
    <QueryClientProvider client={makeClient()}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/dashboard" element={<Dashboard />} />
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

describe("LandingPage", () => {
  beforeEach(() => {
    mockApiFetch.mockReset();
  });

  it("renders the Hiemdall login anchor with configured app id", async () => {
    mockApiFetch.mockResolvedValueOnce(unauthenticated());
    renderAt("/");

    const link = await screen.findByTestId("google-login-link");
    expect(link.getAttribute("href")).toBe(
      "http://localhost:9082/auth/google/start?app=stash",
    );
  });

  it("shows an error banner when ?error= is present", async () => {
    mockApiFetch.mockResolvedValueOnce(unauthenticated());
    renderAt("/?error=access_denied");

    const banner = await screen.findByTestId("login-error-banner");
    expect(banner.textContent).toMatch(/cancelled the Google sign-in/i);
  });

  it("falls back to the generic message for an unknown error code", async () => {
    mockApiFetch.mockResolvedValueOnce(unauthenticated());
    renderAt("/?error=totally_made_up");

    const banner = await screen.findByTestId("login-error-banner");
    expect(banner.textContent).toMatch(/wrong on our side/i);
  });

  it("redirects authenticated users to /dashboard", async () => {
    mockApiFetch.mockResolvedValueOnce(authenticated());
    renderAt("/");

    await waitFor(() =>
      expect(screen.getByText(/Welcome back/i)).toBeInTheDocument(),
    );
  });
});
