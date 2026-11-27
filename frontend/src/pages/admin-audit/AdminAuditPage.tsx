import { AdminAuditView } from "@/features/admin-audit/ui/AdminAuditView";
import { AppHeader } from "@/widgets/app-header/ui/AppHeader";

export function AdminAuditPage() {
  return (
    <div className="min-h-screen bg-background">
      <AppHeader />
      <main className="mx-auto max-w-6xl space-y-6 px-6 py-10">
        <div>
          <h1 className="text-2xl font-semibold">Аудит входа</h1>
          <p className="text-sm text-muted-foreground">
            Журнал событий AuthService: входы, роли, блокировки, OAuth.
          </p>
        </div>
        <AdminAuditView />
      </main>
    </div>
  );
}
