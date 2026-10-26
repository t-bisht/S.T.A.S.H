import { useQuery, type UseQueryResult } from "@tanstack/react-query";

import { fetchMe } from "@/lib/api";

export const ADMIN_SESSION_KEY = ["adminSession"] as const;

/**
 * "Am I signed in?" — resolves to `{username}` or null (401). 401 is a valid
 * outcome, not an error. `retry: false` + `staleTime: 60s` match `useCurrentUser`
 * in `web/`.
 */
export function useAdminSession(): UseQueryResult<{ username: string } | null> {
  return useQuery<{ username: string } | null>({
    queryKey: ADMIN_SESSION_KEY,
    queryFn: fetchMe,
    staleTime: 60_000,
    retry: false,
  });
}
