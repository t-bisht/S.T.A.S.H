import { Navigate, Outlet } from "react-router";

import { AuthLoadingScreen } from "./AuthLoadingScreen";
import { useAdminSession } from "./useAdminSession";

/**
 * Layout-route guard for authenticated pages (spec §3). States:
 *   - resolving          → <AuthLoadingScreen />
 *   - signed in          → <Outlet />
 *   - null (401)         → <Navigate to="/login" />
 *
 * Mid-session 401s on any other call are handled globally by `apiFetch` (§4.3),
 * which hard-redirects to /login?error=session_expired.
 */
export function RequireAdminAuth() {
  const { data, isLoading } = useAdminSession();

  if (isLoading) return <AuthLoadingScreen />;
  if (data) return <Outlet />;
  return <Navigate to="/login" replace />;
}
