import { Navigate, RouterProvider, createBrowserRouter } from "react-router";

import { LoginPage } from "@/features/auth/LoginPage";
import { RequireAdminAuth } from "@/features/auth/RequireAdminAuth";
import { CatalogPage } from "@/features/catalog/CatalogPage";
import { DashboardPage } from "@/features/dashboard/DashboardPage";

import { AppProviders } from "./providers";

// Real URL paths (createBrowserRouter), not a hash router. This requires
// admin_engine's SPA fallback controller in production; Vite handles it in dev.
const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  {
    element: <RequireAdminAuth />,
    children: [
      { path: "/dashboard", element: <DashboardPage /> },
      { path: "/catalog", element: <CatalogPage /> },
    ],
  },
  { path: "*", element: <Navigate to="/dashboard" replace /> },
]);

export function App() {
  return (
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>
  );
}
