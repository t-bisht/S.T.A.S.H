import { useQuery, type UseQueryResult } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import type { MeResponse } from "@/types/auth";

export const CURRENT_USER_KEY = ["me"] as const;

/**
 * "Am I logged in?" — resolves to the current MeResponse or null.
 * 401 is a valid outcome (user not signed in); not treated as an error.
 */
export function useCurrentUser(): UseQueryResult<MeResponse | null> {
  return useQuery<MeResponse | null>({
    queryKey: CURRENT_USER_KEY,
    queryFn: async () => {
      const res = await apiFetch("/me");
      if (res.status === 401) return null;
      if (!res.ok) {
        throw new Error(`/me failed with ${res.status}`);
      }
      return (await res.json()) as MeResponse;
    },
    staleTime: 60_000,
    retry: false,
  });
}
