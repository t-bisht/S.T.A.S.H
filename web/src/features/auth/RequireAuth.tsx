import { Navigate, Outlet } from "react-router";

import { AuthLoadingScreen } from "./AuthLoadingScreen";
import { useCurrentUser } from "./useCurrentUser";

/**
 * Layout-route guard for authenticated pages. Three states:
 *   - resolving   → <AuthLoadingScreen />
 *   - null user   → <Navigate to="/" replace />
 *   - signed in   → <Outlet />
 */
export function RequireAuth() {
  const { data, isLoading } = useCurrentUser();

  if (isLoading) return <AuthLoadingScreen />;
  if (!data) return <Navigate to="/" replace />;
  return <Outlet />;
}
