/**
 * API DTOs shared with user_engine. Shape mirrors phase2_user_engine_tech_spec §7
 * (SessionResponse / MeResponse) and Hiemdall's error-redirect contract (§T1.3).
 */

export interface MeResponse {
  userId: string;
  email: string;
  displayName: string;
  picture: string | null;
}

export interface SessionResponse extends MeResponse {
  isNewUser: boolean;
}

/**
 * Error codes carried on the login page as `/?error=<code>`. Three sources:
 *   - Hiemdall redirect fragment on OAuth failure (`access_denied`, `csrf_mismatch`,
 *     `token_exchange_failed`, `internal_error`).
 *   - SPA-synthesized when the handoff is missing (`missing_handoff`).
 *   - SPA-synthesized when `/auth/session` fails (`session_failed`).
 *
 * Unknown codes fall back to a generic message via `errorMessages`.
 */
export type ErrorCode =
  | "access_denied"
  | "csrf_mismatch"
  | "token_exchange_failed"
  | "internal_error"
  | "missing_handoff"
  | "session_failed";
