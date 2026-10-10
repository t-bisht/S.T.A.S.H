/** Route: /dashboard. Placeholder landing for returning users. */
import { useCurrentUser } from "@/features/auth/useCurrentUser";

export function Dashboard() {
  const { data: user } = useCurrentUser();

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
      </div>
      <p className="mt-4 text-surface-700">
        This would be your dashboard.
      </p>
    </main>
  );
}
