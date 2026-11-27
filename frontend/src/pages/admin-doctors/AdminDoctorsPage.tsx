import { Link } from "react-router-dom";

import { AdminDoctorsView } from "@/features/admin-doctors/ui/AdminDoctorsView";
import { AppHeader } from "@/widgets/app-header/ui/AppHeader";

export function AdminDoctorsPage() {
  return (
    <div className="min-h-screen bg-background">
      <AppHeader />
      <main className="mx-auto max-w-6xl space-y-6 px-6 py-10">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold">Верификация врачей</h1>
            <p className="text-sm text-muted-foreground">
              Подтверждение и отклонение профилей врачей.
            </p>
          </div>
          <Link className="text-sm text-primary hover:underline" to="/admin/users">
            Пользователи и роли
          </Link>
        </div>
        <AdminDoctorsView />
      </main>
    </div>
  );
}
