import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiFetch } from "@/lib/api";

import { AuthReturn } from "./AuthReturn";

vi.mock("@/lib/api", () => ({
  apiFetch: vi.fn(),
}));

const mockApiFetch = vi.mocked(apiFetch);

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

function Landing() {
  return <div data-testid="landing">LANDING</div>;
}

function Register() {
  return <div data-testid="register">REGISTER</div>;
}

function Dashboard() {
  return <div data-testid="dashboard">DASHBOARD</div>;
}

function renderAt(entry: string) {
  return render(
    <QueryClientProvider client={makeClient()}>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path="/" element={<Landing />} />
          <Route path="/auth/return" element={<AuthReturn />} />
          <Route path="/register" element={<Register />} />
          <Route path="/dashboard" element={<Dashboard />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function sessionResponse(isNewUser: boolean): Response {
  return new Response(
    JSON.stringify({
      userId: "u1",
      email: "u@example.com",
      displayName: "User One",
      picture: null,
      isNewUser,
    }),
    { status: 200, headers: { "Content-Type": "application/json" } },
  );
}

describe("AuthReturn", () => {
  beforeEach(() => {
    mockApiFetch.mockReset();
  });

  it("routes to /register when isNewUser=true", async () => {
    mockApiFetch.mockResolvedValueOnce(sessionResponse(true));
    renderAt("/auth/return?handoff=abc");

    await waitFor(() =>
      expect(screen.getByTestId("register")).toBeInTheDocument(),
    );
    expect(mockApiFetch).toHaveBeenCalledWith("/auth/session", {
      method: "POST",
      body: JSON.stringify({ handoff: "abc" }),
    });
  });

  it("routes to /dashboard when isNewUser=false", async () => {
    mockApiFetch.mockResolvedValueOnce(sessionResponse(false));
    renderAt("/auth/return?handoff=xyz");

    await waitFor(() =>
      expect(screen.getByTestId("dashboard")).toBeInTheDocument(),
    );
  });

  it("redirects to /?error=missing_handoff when the handoff is absent", async () => {
    renderAt("/auth/return");

    await waitFor(() =>
      expect(screen.getByTestId("landing")).toBeInTheDocument(),
    );
    expect(mockApiFetch).not.toHaveBeenCalled();
  });

  it("redirects to /?error=session_failed on non-2xx", async () => {
    mockApiFetch.mockResolvedValueOnce(
      new Response("boom", { status: 400 }),
    );
    renderAt("/auth/return?handoff=abc");

    await waitFor(() =>
      expect(screen.getByTestId("landing")).toBeInTheDocument(),
    );
  });
});
