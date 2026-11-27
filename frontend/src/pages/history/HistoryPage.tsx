import { AlertCircle, History, Loader2, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";

import { listAppointments } from "@/entities/appointment/api/appointments";
import type { Appointment } from "@/entities/appointment/model/types";
import { fetchCurrentUser } from "@/entities/session/api/current-user";
import { isDualRole, useRoleMode } from "@/entities/session/model/roleMode";
import { readSession } from "@/entities/session/model/storage";
import type { CurrentUser } from "@/entities/session/model/types";
import { fetchUserProfile, listDoctors } from "@/entities/user/api/userApi";
import { personName } from "@/entities/user/model/display";
import type { DoctorProfile } from "@/entities/user/model/types";
import { AppointmentChatPanel } from "@/features/appointment-chat/ui/AppointmentChatPanel";
import { AppointmentsFeed } from "@/features/appointments-feed/ui/AppointmentsFeed";
import { ApiError, formatApiError } from "@/shared/api/http";
import { Alert, AlertDescription, AlertTitle } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Card, CardContent } from "@/shared/ui/card";
import { RoleModeSwitch } from "@/shared/ui/role-mode-switch";
import { AppHeader } from "@/widgets/app-header/ui/AppHeader";

const HISTORY_STATUSES = ["completed", "cancelled"] as const;

export function HistoryPage() {
  const navigate = useNavigate();

  const [user, setUser] = useState<CurrentUser | null>(null);
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [doctors, setDoctors] = useState<DoctorProfile[]>([]);
  const [patientNames, setPatientNames] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [chatAppointment, setChatAppointment] = useState<Appointment | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [roleMode, setRoleMode] = useRoleMode(user?.roles);

  useEffect(() => {
    document.title = "Telemed — история записей";
  }, []);

  useEffect(() => {
    if (!readSession()) {
      return;
    }
    let cancelled = false;

    async function load() {
      setLoading(true);
      setLoadError(null);
      try {
        const profile = await fetchCurrentUser();
        if (cancelled) {
          return;
        }
        setUser(profile);

        const dual = isDualRole(profile.roles) && !profile.roles.includes("ADMIN");
        const view = dual ? (roleMode === "doctor" ? "doctor" : "patient") : undefined;
        const response = await listAppointments({ limit: 100, view });
        if (cancelled) {
          return;
        }

        const history = (response.items ?? [])
          .filter((appointment) =>
            HISTORY_STATUSES.includes(
              appointment.status as (typeof HISTORY_STATUSES)[number],
            ),
          )
          .sort(
            (left, right) =>
              new Date(right.starts_at).getTime() - new Date(left.starts_at).getTime(),
          );
        setAppointments(history);

        try {
          const doctorsResponse = await listDoctors({ page: 0, size: 100 });
          if (!cancelled) {
            setDoctors(doctorsResponse.content ?? []);
          }
        } catch {
          if (!cancelled) {
            setDoctors([]);
          }
        }

        const profileDoctorMode =
          profile.roles.includes("DOCTOR") && (!dual || roleMode === "doctor");
        if (profileDoctorMode || profile.roles.includes("ADMIN")) {
          const ids = Array.from(new Set(history.map((item) => item.patient_id)))
            .filter((id) => id !== profile.id)
            .slice(0, 50);
          const profiles = await Promise.allSettled(ids.map((id) => fetchUserProfile(id)));
          if (!cancelled) {
            const names: Record<string, string> = { [profile.id]: "Вы" };
            profiles.forEach((result, index) => {
              if (result.status === "fulfilled") {
                names[ids[index]] = personName(result.value);
              }
            });
            setPatientNames(names);
          }
        } else if (!cancelled) {
          setPatientNames({});
        }
      } catch (requestError) {
        if (requestError instanceof ApiError && requestError.status === 401) {
          navigate("/login", { replace: true });
          return;
        }
        if (!cancelled) {
          setLoadError(formatApiError(requestError));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, [navigate, reloadKey, roleMode]);

  const reload = useCallback(() => setReloadKey((value) => value + 1), []);

  if (!readSession()) {
    return <Navigate to="/login" replace />;
  }

  const isAdmin = user?.roles.includes("ADMIN") ?? false;
  const hasDoctorRole = user?.roles.includes("DOCTOR") ?? false;
  const dual = isDualRole(user?.roles) && !isAdmin;
  const isDoctor = hasDoctorRole && (!dual || roleMode === "doctor");
  const doctorsById = new Map(doctors.map((doctor) => [doctor.userId, doctor]));

  function resolveUserName(userId: string): string {
    if (user && userId === user.id) {
      return "Вы";
    }
    const patient = patientNames[userId];
    if (patient) {
      return patient;
    }
    const doctor = doctorsById.get(userId);
    if (doctor) {
      return personName(doctor);
    }
    return userId.slice(0, 8);
  }

  return (
    <div className="min-h-screen bg-background">
      <AppHeader />

      <main className="mx-auto max-w-6xl space-y-6 px-6 py-10">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="space-y-1">
            {dual ? (
              <RoleModeSwitch
                mode={roleMode}
                onChange={setRoleMode}
                className="mb-2"
              />
            ) : null}
            <h1 className="text-2xl font-semibold">История записей</h1>
            <p className="text-sm text-muted-foreground">
              Завершённые и отменённые консультации.
            </p>
          </div>
          <Button variant="outline" size="sm" onClick={reload} disabled={loading}>
            <RefreshCw className={loading ? "h-4 w-4 animate-spin" : "h-4 w-4"} />
            Обновить
          </Button>
        </div>

        {loadError ? (
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertTitle>Не удалось загрузить историю</AlertTitle>
            <AlertDescription>{loadError}</AlertDescription>
          </Alert>
        ) : null}

        {loading ? (
          <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" />
            Загружаем историю
          </div>
        ) : appointments.length === 0 && !loadError ? (
          <Card className="border-dashed">
            <CardContent className="flex flex-col items-center gap-3 py-14 text-center">
              <span className="flex h-12 w-12 items-center justify-center rounded-full bg-accent text-primary">
                <History className="h-6 w-6" />
              </span>
              <h2 className="text-lg font-semibold">История пока пуста</h2>
              <p className="max-w-md text-sm text-muted-foreground">
                Здесь появятся приёмы со статусами «Завершена» и «Отменена».
              </p>
              <Button variant="outline" className="mt-1" onClick={() => navigate("/")}>
                На главную
              </Button>
            </CardContent>
          </Card>
        ) : appointments.length > 0 ? (
          <AppointmentsFeed
            appointments={appointments}
            doctorsById={doctorsById}
            patientNames={patientNames}
            showPatient={isDoctor || isAdmin}
            currentUserId={user?.id ?? ""}
            canManage={false}
            actionId={null}
            onOpenChat={setChatAppointment}
            onConfirm={() => undefined}
            onComplete={() => undefined}
            onCancel={() => undefined}
          />
        ) : null}
      </main>

      {user ? (
        <AppointmentChatPanel
          open={Boolean(chatAppointment)}
          appointment={chatAppointment}
          currentUserId={user.id}
          canStartCall={false}
          resolveName={resolveUserName}
          onCallStarted={() => undefined}
          onClose={() => setChatAppointment(null)}
        />
      ) : null}
    </div>
  );
}
