import { Loader2 } from "lucide-react";

interface AuthLoadingScreenProps {
  message?: string;
}

/**
 * Blocking centered spinner shown while the boot-time session probe resolves
 * (spec §10). No nav.
 */
export function AuthLoadingScreen({
  message = "Loading…",
}: AuthLoadingScreenProps) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex min-h-screen items-center justify-center bg-surface-50"
    >
      <div className="flex flex-col items-center gap-3 text-surface-500">
        <Loader2 className="h-8 w-8 animate-spin text-brand-600" aria-hidden="true" />
        <span className="text-sm">{message}</span>
      </div>
    </div>
  );
}
