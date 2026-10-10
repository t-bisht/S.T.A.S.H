/** Route: /auth/return. Reads handoff / #error from the URL and resolves the login round-trip. */
import { useEffect, useRef } from "react";
import { useLocation, useNavigate } from "react-router";

import { AuthLoadingScreen } from "./AuthLoadingScreen";
import { useCreateSession } from "./useCreateSession";

export function AuthReturn() {
  const navigate = useNavigate();
  const location = useLocation();
  const createSession = useCreateSession();
  // Guard against StrictMode double-invoke which would consume the handoff twice.
  const didRunRef = useRef(false);

  useEffect(() => {
    if (didRunRef.current) return;
    didRunRef.current = true;

    const hash = location.hash;
    if (hash.startsWith("#error=")) {
      const code =
        new URLSearchParams(hash.slice(1)).get("error") ?? "internal_error";
      navigate(`/?error=${encodeURIComponent(code)}`, { replace: true });
      return;
    }

    const handoff = new URLSearchParams(location.search).get("handoff");
    if (!handoff) {
      navigate("/?error=missing_handoff", { replace: true });
      return;
    }

    // Strip handoff from the real URL so a page refresh doesn't retry a
    // consumed one-shot code. Noop under MemoryRouter in tests.
    if (typeof window !== "undefined" && window.history) {
      window.history.replaceState({}, "", "/auth/return");
    }

    createSession.mutate(handoff, {
      onSuccess: (data) => {
        navigate(data.isNewUser ? "/register" : "/dashboard", { replace: true });
      },
      onError: () => {
        navigate("/?error=session_failed", { replace: true });
      },
    });
    // Runs exactly once on mount — re-running on identity changes would
    // re-consume a one-shot handoff.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return <AuthLoadingScreen message="Signing you in…" />;
}
