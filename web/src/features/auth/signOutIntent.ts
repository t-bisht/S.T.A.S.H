/**
 * Remembers that the user asked to sign out, so RequireAuth can tell a
 * deliberate logout (→ plain `/`) from a server-side one (→ signed-out banner).
 *
 * Module-level on purpose: the flag must outlive the Dashboard that started
 * the logout. Set by useLogout, cleared whenever `/me` returns a user again.
 */
let deliberateSignOut = false;

export function markDeliberateSignOut(): void {
  deliberateSignOut = true;
}

export function clearDeliberateSignOut(): void {
  deliberateSignOut = false;
}

export function isDeliberateSignOut(): boolean {
  return deliberateSignOut;
}
