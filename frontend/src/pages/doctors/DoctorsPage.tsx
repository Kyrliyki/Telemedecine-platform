import { DoctorsCatalog } from "@/features/doctors/ui/DoctorsCatalog";
import { AppHeader } from "@/widgets/app-header/ui/AppHeader";

export function DoctorsPage() {
  return (
    <div className="min-h-screen bg-background">
      <AppHeader />
      <main className="mx-auto max-w-6xl space-y-6 px-6 py-10">
        <div>
          <h1 className="text-2xl font-semibold">Врачи</h1>
          <p className="text-sm text-muted-foreground">
            Проверенные специалисты, доступные для консультации.
          </p>
        </div>
        <DoctorsCatalog />
      </main>
    </div>
  );
}
