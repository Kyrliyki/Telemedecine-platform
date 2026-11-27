import {
  AlertCircle,
  CalendarDays,
  CalendarPlus,
  Loader2,
  Phone,
  PhoneCall,
  RefreshCw,
  ShieldCheck,
  X,
} from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";

import {
  cancelAppointment,
  confirmAppointment,
  listAppointments,
} from "@/entities/appointment/api/appointments";
import {
  ACTIVE_APPOINTMENT_STATUSES,
  type Appointment,
} from "@/entities/appointment/model/types";
import { fetchCurrentUser } from "@/entities/session/api/current-user";
import { isDualRole, useRoleMode } from "@/entities/session/model/roleMode";
import { readSession } from "@/entities/session/model/storage";
import type { CurrentUser } from "@/entities/session/model/types";
import { useSession } from "@/entities/session/model/useSession";
import { joinCall } from "@/entities/video/api/video";
import type { CallStartedEvent } from "@/entities/video/model/types";
import { chatSocketUrl } from "@/entities/chat/api/chat";
import { playIncomingCallSound } from "@/shared/lib/call-sound";
import { fetchUserProfile, listDoctors } from "@/entities/user/api/userApi";
import { personName } from "@/entities/user/model/display";
import type { DoctorProfile } from "@/entities/user/model/types";
import {
  AppointmentChatPanel,
  type AppointmentChatPanelHandle,
} from "@/features/appointment-chat/ui/AppointmentChatPanel";
import { CompleteAppointmentDialog } from "@/features/appointment-complete/ui/CompleteAppointmentDialog";
import { AppointmentsFeed } from "@/features/appointments-feed/ui/AppointmentsFeed";
import { useCall } from "@/features/video-call/model/CallProvider";
import { ApiError, formatApiError } from "@/shared/api/http";
import { Alert, AlertDescription, AlertTitle } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Card, CardContent } from "@/shared/ui/card";
import { RoleModeSwitch } from "@/shared/ui/role-mode-switch";
import { useToast } from "@/shared/ui/toast";
import { AppHeader } from "@/widgets/app-header/ui/AppHeader";

export function HomePage() {
  const navigate = useNavigate();
  const session = useSession();
  const { showToast } = useToast();
  const { startCall } = useCall();

  const [user, setUser] = useState<CurrentUser | null>(null);
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [doctors, setDoctors] = useState<DoctorProfile[]>([]);
  const [patientNames, setPatientNames] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [chatAppointment, setChatAppointment] = useState<Appointment | null>(null);
  const [completionTarget, setCompletionTarget] = useState<Appointment | null>(null);
  const [actionId, setActionId] = useState<string | null>(null);
  const [incomingCall, setIncomingCall] = useState<CallStartedEvent["payload"] | null>(null);
  const [joiningCall, setJoiningCall] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [roleMode, setRoleMode] = useRoleMode(user?.roles);

  const chatPanelRef = useRef<AppointmentChatPanelHandle | null>(null);

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

        const roles = profile.roles;
        const dual = isDualRole(roles) && !roles.includes("ADMIN");
        const view = dual ? (roleMode === "doctor" ? "doctor" : "patient") : undefined;
        const appointmentsResponse = await listAppointments({ limit: 100, view });
        if (cancelled) {
          return;
        }

        const now = Date.now();
        const current = (appointmentsResponse.items ?? [])
          .filter(
            (appointment) =>
              ACTIVE_APPOINTMENT_STATUSES.includes(appointment.status) &&
              new Date(appointment.ends_at).getTime() >= now,
          )
          .sort(
            (left, right) =>
              new Date(left.starts_at).getTime() - new Date(right.starts_at).getTime(),
          );
        setAppointments(current);

        let doctorProfiles: DoctorProfile[] = [];
        try {
          const doctorsResponse = await listDoctors({ page: 0, size: 100 });
          doctorProfiles = doctorsResponse.content ?? [];
          if (!cancelled) {
            setDoctors(doctorProfiles);
          }
        } catch {
          if (!cancelled) {
            setDoctors([]);
          }
        }

        const profileDoctorMode =
          profile.roles.includes("DOCTOR") &&
          (!dual || roleMode === "doctor");
        if (profileDoctorMode || profile.roles.includes("ADMIN")) {
          const ids = Array.from(new Set(current.map((item) => item.patient_id)))
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

  useEffect(() => {
    document.title = "Telemed — мои записи";
  }, []);

  useEffect(() => {
    const userId = user?.id;
    if (!userId) {
      return;
    }
    const current = readSession();
    if (!current) {
      return;
    }
    const socket = new WebSocket(chatSocketUrl(current.accessToken));
    socket.onmessage = (event) => {
      try {
        const frame = JSON.parse(event.data as string) as {
          type?: string;
          payload?: CallStartedEvent["payload"];
        };
        if (
          frame.type === "call.started" &&
          frame.payload &&
          frame.payload.patient_id === userId
        ) {
          playIncomingCallSound();
          setIncomingCall(frame.payload);
        }
      } catch {
        
      }
    };
    return () => socket.close();
  }, [user?.id]);

  const reload = useCallback(() => setReloadKey((value) => value + 1), []);

  if (!readSession()) {
    return <Navigate to="/login" replace />;
  }

  const displayName =
    [user?.firstName, user?.lastName].filter(Boolean).join(" ") || user?.email || "Пользователь";
  const isAdmin = user?.roles.includes("ADMIN") ?? false;
  const hasDoctorRole = user?.roles.includes("DOCTOR") ?? false;
  const hasPatientRole = user?.roles.includes("PATIENT") ?? false;
  const dual = isDualRole(user?.roles) && !isAdmin;
  const isDoctor = hasDoctorRole && (!dual || roleMode === "doctor");
  const canBook = hasPatientRole && (!dual || roleMode === "patient");

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

  async function runAction(appointment: Appointment, action: "confirm" | "cancel") {
    if (action === "cancel" && !window.confirm("Отменить запись?")) {
      return;
    }
    setActionId(appointment.id);
    try {
      if (action === "confirm") {
        await confirmAppointment(appointment.id);
        showToast({ description: "Запись подтверждена.", variant: "success" });
      } else {
        const adminActing = user?.roles.includes("ADMIN") ?? false;
        await cancelAppointment(
          appointment.id,
          adminActing ? "Отменено администратором" : "Отменено врачом",
        );
        showToast({ description: "Запись отменена.", variant: "success" });
      }
      reload();
    } catch (requestError) {
      showToast({
        title: "Не удалось выполнить действие",
        description: formatApiError(requestError),
        variant: "error",
        durationMs: 8000,
      });
    } finally {
      setActionId(null);
    }
  }

  function onAppointmentCompleted() {
    setCompletionTarget(null);
    showToast({
      description: "Приём завершён, запись добавлена в медкнижку.",
      variant: "success",
    });
    reload();
  }

  async function joinIncomingCall() {
    if (!incomingCall) {
      return;
    }
    setJoiningCall(true);
    try {
      const joined = await joinCall(incomingCall.call_id, displayName);
      startCall(joined, {
        counterpartName: resolveUserName(incomingCall.doctor_id),
        canEnd: isDoctor || isAdmin,
      });
      setIncomingCall(null);
    } catch (requestError) {
      showToast({
        title: "Не удалось подключиться к звонку",
        description: formatApiError(requestError),
        variant: "error",
        durationMs: 8000,
      });
    } finally {
      setJoiningCall(false);
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <AppHeader />

      <main className="mx-auto max-w-6xl space-y-6 px-6 py-10">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="space-y-1">
            <h1 className="text-2xl font-semibold">
              {isDoctor ? "Приёмы пациентов" : "Мои записи"}
            </h1>
            <p className="text-sm text-muted-foreground">
              {isDoctor
                ? `Здравствуйте, ${displayName}. Здесь собраны записи пациентов к вам.`
                : `Здравствуйте, ${displayName}. Здесь собраны ближайшие консультации.`}
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {dual ? <RoleModeSwitch mode={roleMode} onChange={setRoleMode} /> : null}
            <Button variant="outline" size="sm" onClick={reload} disabled={loading}>
              <RefreshCw className={loading ? "h-4 w-4 animate-spin" : "h-4 w-4"} />
              Обновить
            </Button>
            {canBook ? (
              <Button onClick={() => navigate("/calendar?view=doctor")}>
                <CalendarPlus className="h-4 w-4" />
                Записаться
              </Button>
            ) : null}
          </div>
        </div>

        {isAdmin ? (
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="outline" size="sm">
              <Link to="/admin/users">Пользователи</Link>
            </Button>
            <Button asChild variant="outline" size="sm">
              <Link to="/admin/doctors">Верификация врачей</Link>
            </Button>
            <Button asChild variant="outline" size="sm">
              <Link to="/admin/audit">Аудит входа</Link>
            </Button>
          </div>
        ) : null}

        {session?.mustChangePassword ? (
          <Alert>
            <ShieldCheck className="h-4 w-4" />
            <AlertDescription>
              Администратор требует сменить пароль. Смена пароля пока недоступна в этом
              интерфейсе.
            </AlertDescription>
          </Alert>
        ) : null}

        {loadError ? (
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertTitle>Не удалось загрузить записи</AlertTitle>
            <AlertDescription>{loadError}</AlertDescription>
          </Alert>
        ) : null}

        {loading ? (
          <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" />
            Загружаем записи
          </div>
        ) : appointments.length === 0 && !loadError ? (
          <Card className="border-dashed">
            <CardContent className="flex flex-col items-center gap-3 py-14 text-center">
              <span className="flex h-12 w-12 items-center justify-center rounded-full bg-accent text-primary">
                <CalendarDays className="h-6 w-6" />
              </span>
              <h2 className="text-lg font-semibold">
                {isDoctor ? "Записей пока нет" : "У вас пока нет записей"}
              </h2>
              <p className="max-w-md text-sm text-muted-foreground">
                {canBook
                  ? "Выберите врача и удобное время — заявка сразу появится в списке."
                  : "Здесь появятся приёмы, которые забронируют пациенты."}
              </p>
              {canBook ? (
                <Button className="mt-1" onClick={() => navigate("/calendar?view=doctor")}>
                  <CalendarPlus className="h-4 w-4" />
                  Записаться
                </Button>
              ) : null}
            </CardContent>
          </Card>
        ) : appointments.length > 0 ? (
          <AppointmentsFeed
            appointments={appointments}
            doctorsById={doctorsById}
            patientNames={patientNames}
            showPatient={isDoctor || isAdmin}
            currentUserId={user?.id ?? ""}
            canManage={isDoctor || isAdmin}
            allowEarlyComplete={isAdmin}
            actionId={actionId}
            onOpenChat={setChatAppointment}
            onConfirm={(appointment) => void runAction(appointment, "confirm")}
            onComplete={setCompletionTarget}
            onCancel={(appointment) => void runAction(appointment, "cancel")}
          />
        ) : null}
      </main>

      <CompleteAppointmentDialog
        open={Boolean(completionTarget)}
        appointment={completionTarget}
        isAdmin={isAdmin}
        onClose={() => setCompletionTarget(null)}
        onCompleted={onAppointmentCompleted}
      />

      {incomingCall ? (
        <div className="fixed right-4 top-4 z-[55] w-full max-w-sm">
          <div className="rounded-xl border bg-card p-4 shadow-lg">
            <div className="flex items-start gap-3">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                <PhoneCall className="h-5 w-5" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="font-medium">Врач начал видеозвонок</p>
                <p className="truncate text-sm text-muted-foreground">
                  {resolveUserName(incomingCall.doctor_id)}
                  {appointments.find((item) => item.id === incomingCall.appointment_id)
                    ? ` · ${new Intl.DateTimeFormat("ru-RU", {
                        day: "numeric",
                        month: "short",
                        hour: "2-digit",
                        minute: "2-digit",
                      }).format(
                        new Date(
                          appointments.find(
                            (item) => item.id === incomingCall.appointment_id,
                          )!.starts_at,
                        ),
                      )}`
                    : ""}
                </p>
              </div>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="-mr-1 -mt-1 shrink-0"
                onClick={() => setIncomingCall(null)}
                aria-label="Скрыть уведомление"
              >
                <X className="h-4 w-4" />
              </Button>
            </div>
            <div className="mt-3 flex gap-2">
              <Button
                className="flex-1"
                disabled={joiningCall}
                onClick={() => void joinIncomingCall()}
              >
                {joiningCall ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Phone className="h-4 w-4" />
                )}
                Подключиться
              </Button>
              <Button variant="outline" onClick={() => setIncomingCall(null)}>
                Позже
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      {user ? (
        <AppointmentChatPanel
          ref={chatPanelRef}
          open={Boolean(chatAppointment)}
          appointment={chatAppointment}
          currentUserId={user.id}
          canStartCall={Boolean(
            user &&
              (isDoctor || isAdmin) &&
              (isAdmin || chatAppointment?.doctor_id === user.id),
          )}
          resolveName={resolveUserName}
          onCallStarted={(joined) => {
            const counterpartId =
              joined.call.doctor_id === user.id
                ? joined.call.patient_id
                : joined.call.doctor_id;
            startCall(joined, {
              counterpartName: resolveUserName(counterpartId),
              canEnd: isDoctor || isAdmin,
            });
          }}
          onClose={() => setChatAppointment(null)}
        />
      ) : null}
    </div>
  );
}
