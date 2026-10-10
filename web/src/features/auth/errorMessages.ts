import type { ErrorCode } from "@/types/auth";

/**
 * Login-page error-banner copy keyed by `?error=<code>` on the URL.
 * See types/auth.ts ErrorCode for the full vocabulary.
 */
export const errorMessages: Record<ErrorCode, string> = {
  access_denied: "You cancelled the Google sign-in.",
  csrf_mismatch: "Login session mismatch. Please try signing in again.",
  token_exchange_failed:
    "We couldn't finish signing you in with Google. Please try again.",
  internal_error: "Something went wrong on our side. Please try again.",
  missing_handoff:
    "We didn't receive a sign-in code from Google. Please try again.",
  session_failed:
    "We couldn't start your Stash session. Please try signing in again.",
};

export function resolveErrorMessage(code: string | null | undefined): string | null {
  if (!code) return null;
  if (code in errorMessages) return errorMessages[code as ErrorCode];
  return errorMessages.internal_error;
}
