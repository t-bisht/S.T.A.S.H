import type { ReactNode } from "react";
import { QueryClientProvider, type QueryClient } from "@tanstack/react-query";

import { createQueryClient } from "@/lib/queryClient";

interface AppProvidersProps {
  children: ReactNode;
  // Test seams can inject a pre-populated client; prod uses the lazy factory.
  client?: QueryClient;
}

let defaultClient: QueryClient | undefined;

function getDefaultClient(): QueryClient {
  if (!defaultClient) defaultClient = createQueryClient();
  return defaultClient;
}

export function AppProviders({ children, client }: AppProvidersProps) {
  return (
    <QueryClientProvider client={client ?? getDefaultClient()}>
      {children}
    </QueryClientProvider>
  );
}
