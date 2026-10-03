// ─── GoogleLoginButton.tsx ─────────────────────────────────────
// Reusable "Sign in with Google" button. Pure UI — no auth call,
// no SDK coupling. Caller passes an `onClick` handler which will
// later invoke the Google OAuth flow via `@react-oauth/google` or
// a direct redirect to the backend endpoint.
//
// Keeping this component API-agnostic lets us swap the auth
// backend (direct Google popup vs. server-driven redirect) without
// touching callers.

import { cn } from "@/lib/cn";

interface GoogleLoginButtonProps {
  // Fired when the user clicks the button. API wiring plugs in here.
  onClick?: () => void;
  // Disable interaction (e.g. during an in-flight auth request).
  disabled?: boolean;
  // Optional className override for layout tweaks at call sites.
  className?: string;
  // Visible label — defaults to Google's recommended copy.
  label?: string;
}

/**
 * GoogleLoginButton
 * Renders Google's brand-approved "G" glyph + a label.
 * Styled as a white pill with a subtle border, matching Google's
 * official sign-in button guidelines (approximation, not exact asset).
 */
export function GoogleLoginButton({
  onClick,
  disabled = false,
  className,
  label = "Sign in with Google",
}: GoogleLoginButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      className={cn(
        // Base layout — flex row, pill shape, comfortable touch target.
        "inline-flex items-center justify-center gap-3",
        "h-12 px-6 rounded-full",
        // Surface — white background, hairline border matches Google spec.
        "bg-surface-0 border border-surface-200",
        // Typography — medium weight, neutral text.
        "text-sm font-medium text-surface-900",
        // Interaction — subtle hover + focus ring in brand color.
        "transition-colors hover:bg-surface-50",
        "focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2",
        // Disabled state — fade + block pointer.
        "disabled:opacity-60 disabled:cursor-not-allowed",
        className,
      )}
    >
      <GoogleGlyph />
      <span>{label}</span>
    </button>
  );
}

/**
 * GoogleGlyph
 * Google's four-color "G" rendered as inline SVG so we avoid
 * shipping an image asset for a single icon. 18x18 matches Google's
 * documented sign-in button icon size.
 */
function GoogleGlyph() {
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
