import { useMutation, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import type { MeResponse, SessionResponse } from "@/types/auth";

import { CURRENT_USER_KEY } from "./useCurrentUser";

/**
 * Redeems a Hiemdall handoff code via POST /auth/session. On success, seeds the
 * `me` cache from the response body so RequireAuth doesn't have to re-fetch.
 * Throws on non-2xx — AuthReturn maps that to the `/?error=session_failed` path.
 */
export function useCreateSession() {
  const queryClient = useQueryClient();

  return useMutation<SessionResponse, Error, string>({
    mutationFn: async (handoff: string) => {
      const res = await apiFetch("/auth/session", {
        method: "POST",
        body: JSON.stringify({ handoff }),
      });
      if (!res.ok) {
        throw new Error(`session mint failed: ${res.status}`);
      }
      return (await res.json()) as SessionResponse;
    },
    onSuccess: (data) => {
      const me: MeResponse = {
        userId: data.userId,
        email: data.email,
        displayName: data.displayName,
        picture: data.picture,
      };
      queryClient.setQueryData<MeResponse>(CURRENT_USER_KEY, me);
    },
  });
}
