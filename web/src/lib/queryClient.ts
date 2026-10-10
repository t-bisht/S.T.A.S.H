import { QueryClient } from "@tanstack/react-query";

/**
 * Single app-wide QueryClient. Auth queries use `retry: false` locally (401 is
 * a valid outcome, not transient). Default retry stays conservative so a
 * future server call doesn't thrash on a hard failure.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: 1,
        refetchOnWindowFocus: false,
        staleTime: 30_000,
      },
      mutations: {
        retry: 0,
      },
    },
  });
}
