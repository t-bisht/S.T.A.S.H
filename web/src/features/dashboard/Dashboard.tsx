/** Route: /dashboard. Placeholder landing for returning users. */
import { useNavigate } from "react-router";

import { Button } from "@/components/ui/button";
import { useCurrentUser } from "@/features/auth/useCurrentUser";
import { useLogout } from "@/features/auth/useLogout";

export function Dashboard() {
  const { data: user } = useCurrentUser();
  const logout = useLogout();
  const navigate = useNavigate();

  // Navigate on settle: useLogout drops the `me` cache on success or failure alike.
  const handleLogout = () =>
    logout.mutate(undefined, {
      onSettled: () => navigate("/", { replace: true }),
    });

  return (
    <main className="mx-auto max-w-xl p-8">
      <div className="flex items-center gap-4">
        {user?.picture && (
          <img
            src={user.picture}
            alt={user.displayName}
            referrerPolicy="no-referrer"
            className="h-16 w-16 rounded-full border border-surface-200 object-cover"
          />
        )}
        <h1 className="text-2xl font-semibold text-surface-900">
          Welcome back{user?.displayName ? `, ${user.displayName}` : ""}
        </h1>
        <Button
          variant="outline"
          size="sm"
          className="ml-auto"
          onClick={handleLogout}
          disabled={logout.isPending}
        >
          {logout.isPending ? "Signing out…" : "Sign out"}
        </Button>
      </div>
      <p className="mt-4 text-surface-700">
        This would be your dashboard.
      </p>
    </main>
  );
}
