import { AdminUsersView } from "@/features/admin-users/ui/AdminUsersView";
import { AppHeader } from "@/widgets/app-header/ui/AppHeader";

export function AdminUsersPage() {
  return (
    <div className="min-h-screen bg-background">
      <AppHeader />
      <main className="mx-auto max-w-6xl space-y-6 px-6 py-10">
        <div>
          <h1 className="text-2xl font-semibold">Администрирование</h1>
          <p className="text-sm text-muted-foreground">
            Приглашения, роли и блокировки.
          </p>
        </div>
        <AdminUsersView />
      </main>
    </div>
  );
}
