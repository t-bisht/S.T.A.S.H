// ─── GoogleLoginButton.tsx ─────────────────────────────────────
// "Sign in with Google" affordance. Renders Google's four-color "G"
// glyph next to a label. Two call shapes are supported:
//
//   - As a plain button:  <GoogleLoginButton onClick={...} />
//   - As an anchor link:  <GoogleLoginButton asChild><a href={...}>Sign in</a></GoogleLoginButton>
//
// The anchor form is the Phase 3 default — Hiemdall-brokered OAuth needs a
// real browser navigation, not a JS fetch. Styling + accessibility are shared.

import * as React from "react";

import { Button, type ButtonProps } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export interface GoogleLoginButtonProps
  extends Omit<ButtonProps, "variant" | "size"> {
  // Visible label — defaults to Google's recommended copy.
  label?: string;
}

export const GoogleLoginButton = React.forwardRef<
  HTMLButtonElement,
  GoogleLoginButtonProps
>(
  (
    { asChild = false, className, label = "Sign in with Google", children, ...rest },
    ref,
  ) => {
    const content = children ?? (
      <>
        <GoogleGlyph />
        <span>{label}</span>
      </>
    );

    return (
      <Button
        ref={ref}
        asChild={asChild}
        variant="outline"
        size="pill"
        aria-label={asChild ? undefined : label}
        className={cn(
          "gap-3 border-surface-200 bg-surface-0 text-surface-900 hover:bg-surface-50",
          className,
        )}
        {...rest}
      >
        {content}
      </Button>
    );
  },
);
GoogleLoginButton.displayName = "GoogleLoginButton";

/**
 * Four-color "G" rendered as inline SVG to avoid shipping an image asset for
 * a single icon. 18x18 matches Google's documented sign-in button spec.
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
