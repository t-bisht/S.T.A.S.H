import { useEffect, useRef } from "react";
import { Navigate, Outlet } from "react-router";

import { SIGNED_OUT_PATH } from "@/lib/api";

import { AuthLoadingScreen } from "./AuthLoadingScreen";
import { isDeliberateSignOut } from "./signOutIntent";
import { useCurrentUser } from "./useCurrentUser";

/**
 * Layout-route guard for authenticated pages. States:
 *   - resolving                    → <AuthLoadingScreen />
 *   - signed in                    → <Outlet />
 *   - null user on first load      → <Navigate to="/" />
 *   - null user after signed in    → <Navigate to="/?error=signed_out" />
 *     (session vanished server-side, e.g. forced logout), unless the user
 *     clicked "Sign out" themselves → <Navigate to="/" />.
 */
export function RequireAuth() {
  const { data, isLoading } = useCurrentUser();
  const wasSignedIn = useRef(false);

  useEffect(() => {
    if (data) wasSignedIn.current = true;
  }, [data]);

  if (isLoading) return <AuthLoadingScreen />;
  if (data) return <Outlet />;

  const lostSession = wasSignedIn.current && !isDeliberateSignOut();
  return <Navigate to={lostSession ? SIGNED_OUT_PATH : "/"} replace />;
}
