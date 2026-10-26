import { useMutation, useQueryClient } from "@tanstack/react-query";

import { logout } from "@/lib/api";

import { ADMIN_SESSION_KEY } from "./useAdminSession";

/**
 * POST /admin/logout. Clears the session cache regardless of outcome — the
 * caller navigates to /login either way (§4.4: the server may have already
 * invalidated the session).
 */
export function useLogout() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, void>({
    mutationFn: logout,
    onSettled: () => {
      queryClient.setQueryData(ADMIN_SESSION_KEY, null);
      queryClient.removeQueries({ queryKey: ADMIN_SESSION_KEY });
    },
  });
}
