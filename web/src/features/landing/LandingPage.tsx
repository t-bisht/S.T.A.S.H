/** Route: /. Public landing + sign-in. Redirects to /dashboard when authenticated. */
import { Navigate, useSearchParams } from "react-router";

import { GoogleLoginButton } from "@/components/GoogleLoginButton";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { AuthLoadingScreen } from "@/features/auth/AuthLoadingScreen";
import { resolveErrorMessage } from "@/features/auth/errorMessages";
import { useCurrentUser } from "@/features/auth/useCurrentUser";

/**
 * Builds the Hiemdall Google-login URL from runtime config. Hard navigation
 * (not fetch) — OAuth needs a real top-level redirect to Google.
 */
function hiemdallLoginUrl(): string {
  const base = window.ENV.HIEMDALL_BASE_URL.replace(/\/+$/, "");
  const app = encodeURIComponent(window.ENV.APP_ID);
  return `${base}/auth/google/start?app=${app}`;
}

export function LandingPage() {
  const { data: user, isLoading } = useCurrentUser();
  const [params] = useSearchParams();
  const errorMessage = resolveErrorMessage(params.get("error"));

  if (isLoading) return <AuthLoadingScreen />;
  if (user) return <Navigate to="/dashboard" replace />;

  const loginHref = hiemdallLoginUrl();

  return (
    <main className="min-h-screen bg-surface-50 text-surface-900 flex flex-col">
      <header className="px-6 py-5">
        <span className="text-sm font-semibold tracking-widest text-brand-700">
          STASH
        </span>
      </header>

      <section className="flex-1 flex items-center justify-center px-6">
        <div className="w-full max-w-xl text-center">
          <Logo />

          <h1 className="mt-6 text-5xl sm:text-6xl font-bold tracking-tight text-surface-900">
            Stash
          </h1>

          <p className="mt-3 text-base sm:text-lg text-surface-700">
            <span className="font-semibold text-brand-700">S</span>mart{" "}
            <span className="font-semibold text-brand-700">T</span>racking,{" "}
            <span className="font-semibold text-brand-700">A</span>nalysis, &{" "}
            <span className="font-semibold text-brand-700">S</span>pend{" "}
            <span className="font-semibold text-brand-700">H</span>ub
          </p>

          <p className="mt-6 text-sm sm:text-base text-surface-500 max-w-md mx-auto">
            Pull receipts from your inbox. Watch where the money goes.
            Decide what changes.
          </p>

          <div className="mt-10 flex justify-center">
            <GoogleLoginButton asChild>
              <a href={loginHref} data-testid="google-login-link">
                <GoogleGlyphSlot />
                <span>Sign in with Google</span>
              </a>
            </GoogleLoginButton>
          </div>

          {errorMessage && (
            <div className="mt-4 flex justify-center">
              <Alert
                variant="destructive"
                data-testid="login-error-banner"
                className="max-w-md text-left"
              >
                <AlertDescription>{errorMessage}</AlertDescription>
              </Alert>
            </div>
          )}

          <p className="mt-4 text-xs text-surface-500">
            By continuing, you agree to let Stash read receipts from your
            Gmail. We never send email on your behalf.
          </p>
        </div>
      </section>

      <footer className="px-6 py-4 text-center text-xs text-surface-500">
        &copy; {new Date().getFullYear()} Stash
      </footer>
    </main>
  );
}

function Logo() {
  return (
    <div
      className="mx-auto h-16 w-16 rounded-2xl bg-brand-600 flex items-center justify-center shadow-sm"
      aria-hidden="true"
    >
      <span className="text-3xl font-bold text-surface-0">S</span>
    </div>
  );
}

/**
 * Google glyph duplicated here because <GoogleLoginButton asChild> replaces
 * the default button body with its own children. Keeps the anchor consumer
 * explicit about what renders inside.
 */
function GoogleGlyphSlot() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="18"
      height="18"
      viewBox="0 0 48 48"
      aria-hidden="true"
    >
      <path
        fill="#FFC107"
        d="M43.6 20.5H42V20H24v8h11.3C33.9 32.4 29.4 35.5 24 35.5c-6.3 0-11.5-5.1-11.5-11.5S17.7 12.5 24 12.5c2.9 0 5.6 1.1 7.6 2.9l5.7-5.7C33.6 6.3 29 4.5 24 4.5 13.2 4.5 4.5 13.2 4.5 24S13.2 43.5 24 43.5 43.5 34.8 43.5 24c0-1.2-.1-2.3-.4-3.5z"
      />
      <path
        fill="#FF3D00"
        d="M6.3 14.1l6.6 4.8c1.8-4.3 6-7.4 10.9-7.4 2.9 0 5.6 1.1 7.6 2.9l5.7-5.7C33.6 6.3 29 4.5 24 4.5 16.3 4.5 9.7 8.6 6.3 14.1z"
      />
      <path
        fill="#4CAF50"
        d="M24 43.5c5 0 9.5-1.9 12.9-5l-6-5.1c-2 1.4-4.4 2.1-6.9 2.1-5.3 0-9.8-3.1-11.3-7.4l-6.5 5C9.5 39.3 16.2 43.5 24 43.5z"
      />
      <path
        fill="#1976D2"
        d="M43.6 20.5H42V20H24v8h11.3c-.8 2.3-2.3 4.2-4.3 5.5l6 5.1c3.4-3.2 5.5-7.8 5.5-13.1 0-1.2-.1-2.3-.4-3.5z"
      />
    </svg>
  );
}
