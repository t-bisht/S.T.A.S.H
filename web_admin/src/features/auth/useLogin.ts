import { useMutation, useQueryClient } from "@tanstack/react-query";

import { login } from "@/lib/api";

import { ADMIN_SESSION_KEY } from "./useAdminSession";

export interface LoginVars {
  username: string;
  password: string;
}

/**
 * POST /admin/login. On success, invalidate the session query so the probe
 * re-runs; the caller (LoginPage) navigates to /dashboard (§4.2).
 */
export function useLogin() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, LoginVars>({
    mutationFn: ({ username, password }) => login(username, password),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ADMIN_SESSION_KEY });
    },
  });
}
