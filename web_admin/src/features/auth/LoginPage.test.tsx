import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AdminApiError, fetchMe, login } from "@/lib/api";

import { LoginPage } from "./LoginPage";

vi.mock("@/lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  login: vi.fn(),
  fetchMe: vi.fn(),
}));

const mockLogin = vi.mocked(login);
const mockFetchMe = vi.mocked(fetchMe);

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

function renderLogin() {
  return render(
    <QueryClientProvider client={makeClient()}>
      <MemoryRouter initialEntries={["/login"]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/dashboard" element={<div data-testid="dashboard">DASHBOARD</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("LoginPage", () => {
  beforeEach(() => {
    mockLogin.mockReset();
    mockFetchMe.mockReset();
    // Not signed in — the boot probe resolves to null so the form renders.
    mockFetchMe.mockResolvedValue(null);
  });

  it("shows an inline error on 401 without redirecting", async () => {
    mockLogin.mockRejectedValueOnce(
      new AdminApiError("Invalid credentials", 401, "unauthorized"),
    );
    const user = userEvent.setup();
    renderLogin();

    const username = await screen.findByLabelText("Username");
    await user.type(username, "admin");
    await user.type(screen.getByLabelText("Password"), "wrong");
    await user.click(screen.getByRole("button", { name: /sign in/i }));

    expect(
      await screen.findByText("Incorrect username or password."),
    ).toBeInTheDocument();
    // Still on the login page — no navigation to /dashboard.
    expect(screen.queryByTestId("dashboard")).not.toBeInTheDocument();
    // Username is preserved.
    expect(screen.getByLabelText("Username")).toHaveValue("admin");
  });

  it("navigates to /dashboard on success", async () => {
    mockLogin.mockResolvedValueOnce(undefined);
    const user = userEvent.setup();
    renderLogin();

    await user.type(await screen.findByLabelText("Username"), "admin");
    await user.type(screen.getByLabelText("Password"), "right");
    await user.click(screen.getByRole("button", { name: /sign in/i }));

    await waitFor(() =>
      expect(screen.getByTestId("dashboard")).toBeInTheDocument(),
    );
  });
});
