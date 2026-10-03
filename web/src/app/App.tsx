// ─── App.tsx ───────────────────────────────────────────────────
// Root application component. Currently a single-screen app that
// renders the public landing page. Routing will be introduced once
// authenticated screens exist.

import { LandingPage } from "@/features/landing/LandingPage";

export function App() {
  return <LandingPage />;
}
