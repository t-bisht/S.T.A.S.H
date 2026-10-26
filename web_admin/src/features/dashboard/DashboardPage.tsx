import { useNavigate } from "react-router";
import { ChevronRight, Store } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";

import { useAdminSession } from "@/features/auth/useAdminSession";
import { useLogout } from "@/features/auth/useLogout";

/**
 * Post-login landing. A menu of admin tasks; the first (and currently only)
 * item opens the Digital Shopping Mart catalog browser. Sign-out lives in the
 * header row.
 */

interface MenuItem {
  key: string;
  label: string;
  description: string;
  icon: typeof Store;
  to: string;
}

const MENU: MenuItem[] = [
  {
    key: "catalog",
    label: "Manage Digital Shopping Mart Catalog",
    description: "Browse categories, subcategories and items.",
    icon: Store,
    to: "/catalog",
  },
];

export function DashboardPage() {
  const { data: session } = useAdminSession();
  const navigate = useNavigate();
  const logoutMutation = useLogout();

  function handleLogout() {
    logoutMutation.mutate(undefined, {
      onSettled: () => navigate("/login", { replace: true }),
    });
  }

  return (
    <div className="min-h-screen bg-surface-50 px-4 py-8">
      <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl font-semibold text-surface-900">
              Welcome to Admin Dashboard
            </h1>
            <p className="text-sm text-surface-500">
              {session ? `Signed in as ${session.username}.` : "Signed in."}
            </p>
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleLogout}
            disabled={logoutMutation.isPending}
          >
            Sign out
          </Button>
        </div>

        <nav className="flex flex-col gap-3" aria-label="Admin tasks">
          {MENU.map((item) => {
            const Icon = item.icon;
            return (
              <Card
                key={item.key}
                role="button"
                tabIndex={0}
                onClick={() => navigate(item.to)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    navigate(item.to);
                  }
                }}
                className="cursor-pointer transition-colors hover:bg-surface-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
              >
                <CardContent className="flex items-center gap-4 py-4">
                  <span className="flex h-10 w-10 items-center justify-center rounded-md bg-brand-50 text-brand-600">
                    <Icon className="h-5 w-5" aria-hidden="true" />
                  </span>
                  <span className="flex flex-col">
                    <span className="text-sm font-medium text-surface-900">
                      {item.label}
                    </span>
                    <span className="text-sm text-surface-500">
                      {item.description}
                    </span>
                  </span>
                  <ChevronRight
                    className="ml-auto h-5 w-5 text-surface-400"
                    aria-hidden="true"
                  />
                </CardContent>
              </Card>
            );
          })}
        </nav>
      </div>
    </div>
  );
}
