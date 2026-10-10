import { Navigate, RouterProvider, createBrowserRouter } from "react-router";

import { AuthReturn } from "@/features/auth/AuthReturn";
import { RequireAuth } from "@/features/auth/RequireAuth";
import { Dashboard } from "@/features/dashboard/Dashboard";
import { LandingPage } from "@/features/landing/LandingPage";
import { Register } from "@/features/onboarding/Register";

import { AppProviders } from "./providers";

const router = createBrowserRouter([
  { path: "/", element: <LandingPage /> },
  { path: "/auth/return", element: <AuthReturn /> },
  {
    element: <RequireAuth />,
    children: [
      { path: "/register", element: <Register /> },
      { path: "/dashboard", element: <Dashboard /> },
    ],
  },
  { path: "*", element: <Navigate to="/" replace /> },
]);

export function App() {
  return (
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>
  );
}
