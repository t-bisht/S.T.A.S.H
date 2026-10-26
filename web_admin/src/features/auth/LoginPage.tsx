import { useState, type FormEvent } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router";
import { Loader2 } from "lucide-react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { AdminApiError } from "@/lib/api";

import { AuthLoadingScreen } from "./AuthLoadingScreen";
import { useAdminSession } from "./useAdminSession";
import { useLogin } from "./useLogin";

/**
 * Username + password login (spec §4.2). If the boot probe finds an existing
 * session, redirect straight to /dashboard before rendering the form.
 */
export function LoginPage() {
  const { data: session, isLoading } = useAdminSession();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const loginMutation = useLogin();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [showExpired, setShowExpired] = useState(
    searchParams.get("error") === "session_expired",
  );

  // Probe still resolving — show the spinner so we never flash the form at an
  // already-authenticated user (§4.2).
  if (isLoading) return <AuthLoadingScreen message="Checking session…" />;
  if (session) return <Navigate to="/dashboard" replace />;

  // The expired banner and any prior error clear once the user starts typing.
  function handleTyping() {
    if (showExpired) setShowExpired(false);
    if (formError) setFormError(null);
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    loginMutation.mutate(
      { username, password },
      {
        onSuccess: () => navigate("/dashboard", { replace: true }),
        onError: (err) => {
          const is401 = err instanceof AdminApiError && err.status === 401;
          setFormError(
            is401
              ? "Incorrect username or password."
              : "Sign-in failed — try again.",
          );
          // Do not clear the username field (§4.2).
        },
      },
    );
  }

  const pending = loginMutation.isPending;

  return (
    <div className="flex min-h-screen items-center justify-center bg-surface-50 px-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>Stash Admin</CardTitle>
          <CardDescription>Sign in to the admin console.</CardDescription>
        </CardHeader>
        <CardContent>
          {showExpired && (
            <Alert className="mb-4" role="status">
              <AlertDescription>
                <div className="flex items-start justify-between gap-3">
                  <span>Your session has expired. Please sign in again.</span>
                  <button
                    type="button"
                    aria-label="Dismiss"
                    className="text-surface-500 hover:text-surface-900"
                    onClick={() => setShowExpired(false)}
                  >
                    ×
                  </button>
                </div>
              </AlertDescription>
            </Alert>
          )}

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="username"
                className="text-sm font-medium text-surface-700"
              >
                Username
              </label>
              <input
                id="username"
                name="username"
                type="text"
                autoComplete="username"
                value={username}
                disabled={pending}
                onChange={(e) => {
                  setUsername(e.target.value);
                  handleTyping();
                }}
                className="h-10 rounded-md border border-surface-200 bg-surface-0 px-3 text-sm text-surface-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 disabled:opacity-50"
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="password"
                className="text-sm font-medium text-surface-700"
              >
                Password
              </label>
              <input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                value={password}
                disabled={pending}
                onChange={(e) => {
                  setPassword(e.target.value);
                  handleTyping();
                }}
                className="h-10 rounded-md border border-surface-200 bg-surface-0 px-3 text-sm text-surface-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 disabled:opacity-50"
              />
            </div>

            {formError && (
              <Alert variant="destructive">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            )}

            <Button type="submit" disabled={pending}>
              {pending && (
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
              )}
              Sign in
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
