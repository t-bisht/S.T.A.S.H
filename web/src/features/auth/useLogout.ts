import { useMutation, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

import { CURRENT_USER_KEY } from "./useCurrentUser";

/**
 * Clears the server session (DELETE row + Set-Cookie Max-Age=0) and drops the
 * local `me` cache. UI consumer is Phase 4 T4.3; shipped now for completeness.
 */
export function useLogout() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, void>({
    mutationFn: async () => {
      const res = await apiFetch("/auth/logout", { method: "POST" });
      if (!res.ok && res.status !== 204) {
        throw new Error(`logout failed: ${res.status}`);
      }
    },
    onSettled: () => {
      queryClient.setQueryData(CURRENT_USER_KEY, null);
      queryClient.removeQueries({ queryKey: CURRENT_USER_KEY });
    },
  });
}
