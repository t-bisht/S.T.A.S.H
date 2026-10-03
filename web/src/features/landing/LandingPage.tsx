// ─── LandingPage.tsx ───────────────────────────────────────────
// Public landing / sign-in screen for Stash.
// Shows the product name, acronym expansion, a short pitch, and a
// single call-to-action: Sign in with Google.
//
// No API wiring yet. The login handler is a stub that logs intent;
// it will be replaced with a Google OAuth call once the user_engine
// backend endpoints are in place.

import { GoogleLoginButton } from "@/components/GoogleLoginButton";

/**
 * LandingPage
 * Full-viewport hero. Centered title + tagline + CTA.
 * Pure presentational — no data fetching, no auth state.
 */
export function LandingPage() {
  // Stub handler — replace with real OAuth call later.
  // Kept inline (no useCallback) since this component has no memoized
  // children that would re-render from identity changes.
  const handleGoogleLogin = () => {
    // eslint-disable-next-line no-console
    console.info("[Stash] Google login clicked — backend wiring pending.");
  };

  return (
    <main className="min-h-screen bg-surface-50 text-surface-900 flex flex-col">
      {/* Top-left wordmark — thin header, no navigation yet. */}
      <header className="px-6 py-5">
        <span className="text-sm font-semibold tracking-widest text-brand-700">
          STASH
        </span>
      </header>

      {/* Hero — centered vertically in remaining space. */}
      <section className="flex-1 flex items-center justify-center px-6">
        <div className="w-full max-w-xl text-center">
          <Logo />

          {/* Product name */}
          <h1 className="mt-6 text-5xl sm:text-6xl font-bold tracking-tight text-surface-900">
            Stash
          </h1>

          {/* Acronym expansion — subdued, slightly smaller. */}
          <p className="mt-3 text-base sm:text-lg text-surface-700">
            <span className="font-semibold text-brand-700">S</span>mart{" "}
            <span className="font-semibold text-brand-700">T</span>racking,{" "}
            <span className="font-semibold text-brand-700">A</span>nalysis, &{" "}
            <span className="font-semibold text-brand-700">S</span>pend{" "}
            <span className="font-semibold text-brand-700">H</span>ub
          </p>

          {/* One-line pitch */}
          <p className="mt-6 text-sm sm:text-base text-surface-500 max-w-md mx-auto">
            Pull receipts from your inbox. Watch where the money goes.
            Decide what changes.
          </p>

          {/* Primary CTA */}
          <div className="mt-10 flex justify-center">
            <GoogleLoginButton onClick={handleGoogleLogin} />
          </div>

          {/* Legal / consent footnote under CTA. */}
          <p className="mt-4 text-xs text-surface-500">
            By continuing, you agree to let Stash read receipts from your
            Gmail. We never send email on your behalf.
          </p>
        </div>
      </section>

      {/* Footer — minimal, bottom-aligned. */}
      <footer className="px-6 py-4 text-center text-xs text-surface-500">
        &copy; {new Date().getFullYear()} Stash
      </footer>
    </main>
  );
}

/**
 * Logo
 * Simple brand mark — rounded square with the letter "S".
 * Inline so the landing page has no external image dependency.
 */
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
