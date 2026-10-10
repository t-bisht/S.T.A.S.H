/** Route: /register. Placeholder landing for first-time users. */
import { useCurrentUser } from "@/features/auth/useCurrentUser";

export function Register() {
  const { data: user } = useCurrentUser();
  if (!user) return null; // RequireAuth already handled unauth.

  return (
    <main className="mx-auto max-w-xl p-8">
      <div className="flex items-center gap-4">
        {user.picture && (
          <img
            src={user.picture}
            alt={user.displayName}
            referrerPolicy="no-referrer"
            className="h-16 w-16 rounded-full border border-surface-200 object-cover"
          />
        )}
        <h1 className="text-2xl font-semibold text-surface-900">
          Hello {user.displayName}
        </h1>
      </div>
      <p className="mt-4 text-surface-700">
        We are going to register you here.
      </p>
      {user.picture && (
        <p className="mt-4 break-all text-sm text-surface-500">
          Picture URL:{" "}
          <a
            href={user.picture}
            className="text-brand-700 underline"
            target="_blank"
            rel="noreferrer"
          >
            {user.picture}
          </a>
        </p>
      )}
    </main>
  );
}
