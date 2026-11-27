import {
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Loader2,
  RefreshCw,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";

import { listAppointments } from "@/entities/appointment/api/appointments";
import type { Appointment } from "@/entities/appointment/model/types";
import { getDoctorAvailability } from "@/entities/schedule/api/schedule";
import type { ScheduleSlot } from "@/entities/schedule/model/types";
import { fetchCurrentUser } from "@/entities/session/api/current-user";
import { isDualRole, useRoleMode } from "@/entities/session/model/roleMode";
import { readSession } from "@/entities/session/model/storage";
import type { CurrentUser } from "@/entities/session/model/types";
import { fetchUserProfile, listDoctors } from "@/entities/user/api/userApi";
import { personName } from "@/entities/user/model/display";
import type { DoctorProfile } from "@/entities/user/model/types";
import { BookingDialog } from "@/features/appointment-book/ui/BookingDialog";
import { ApiError, formatApiError } from "@/shared/api/http";
import { Alert, AlertDescription, AlertTitle } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/ui/card";
import { Label } from "@/shared/ui/label";
import { Select } from "@/shared/ui/select";
import { RoleModeSwitch } from "@/shared/ui/role-mode-switch";
import { useToast } from "@/shared/ui/toast";
import { cn } from "@/shared/lib/cn";
import { AppHeader } from "@/widgets/app-header/ui/AppHeader";

const STATUS_LABELS: Record<string, string> = {
  requested: "Ожидает",
  confirmed: "Подтверждена",
  cancelled: "Отменена",
  completed: "Завершена",
  no_show: "Неявка",
};

interface BookingSlot {
  doctorId: string;
  startsAt: string;
  label: string;
}

export function CalendarPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { showToast } = useToast();

  const [user, setUser] = useState<CurrentUser | null>(null);
  const [doctors, setDoctors] = useState<DoctorProfile[]>([]);
  const [selectedDoctorId, setSelectedDoctorId] = useState("");
  const [view, setView] = useState<"mine" | "doctor">(() =>
    searchParams.get("view") === "doctor" ? "doctor" : "mine",
  );
  const [month, setMonth] = useState(() => startOfMonth(new Date()));
  const [selectedDate, setSelectedDate] = useState(() => toIsoDate(new Date()));
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [patientNames, setPatientNames] = useState<Record<string, string>>({});
  const [slots, setSlots] = useState<ScheduleSlot[]>([]);
  const [loading, setLoading] = useState(true);
  const [slotsLoading, setSlotsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [slotsError, setSlotsError] = useState<string | null>(null);
  const [bookingSlot, setBookingSlot] = useState<BookingSlot | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [roleMode, setRoleMode] = useRoleMode(user?.roles);

  const reload = useCallback(() => setRefreshKey((value) => value + 1), []);

  useEffect(() => {
    document.title = "Telemed — календарь";
  }, []);

  useEffect(() => {
    if (!readSession()) {
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    Promise.all([fetchCurrentUser(), listDoctors({ page: 0, size: 100 })])
      .then(async ([profile, doctorPage]) => {
        if (cancelled) {
          return;
        }
        const dual = isDualRole(profile.roles) && !profile.roles.includes("ADMIN");
        const appointmentView = dual
          ? roleMode === "doctor"
            ? "doctor"
            : "patient"
          : undefined;
        const appointmentPage = await listAppointments({
          limit: 100,
          view: appointmentView,
        });
        if (cancelled) {
          return;
        }
        setUser(profile);
        const doctorList = doctorPage.content ?? [];
        setDoctors(doctorList);
        setSelectedDoctorId((current) => current || doctorList[0]?.userId || "");
        const items = appointmentPage.items ?? [];
        setAppointments(items);

        if (profile.roles.includes("DOCTOR") && (!dual || roleMode === "doctor")) {
          const ids = Array.from(new Set(items.map((item) => item.patient_id)))
            .filter((id) => id !== profile.id)
            .slice(0, 100);
          const profiles = await Promise.allSettled(ids.map((id) => fetchUserProfile(id)));
          if (!cancelled) {
            const names: Record<string, string> = {};
            profiles.forEach((result, index) => {
              if (result.status === "fulfilled") {
                names[ids[index]] = personName(result.value);
              }
            });
            setPatientNames(names);
          }
        }
      })
      .catch((requestError) => {
        if (requestError instanceof ApiError && requestError.status === 401) {
          navigate("/login", { replace: true });
          return;
        }
        if (!cancelled) {
          setError(formatApiError(requestError));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [navigate, refreshKey, roleMode]);

  useEffect(() => {
    if (view !== "doctor" || !selectedDoctorId) {
      setSlots([]);
      setSlotsError(null);
      setSlotsLoading(false);
      return;
    }
    const today = toIsoDate(new Date());
    const monthStart = toIsoDate(startOfMonth(month));
    const monthEnd = toIsoDate(endOfMonth(month));
    if (monthEnd < today) {
      setSlots([]);
      setSlotsError(null);
      setSlotsLoading(false);
      return;
    }
    const from = monthStart < today ? today : monthStart;

    let cancelled = false;
    setSlotsLoading(true);
    setSlotsError(null);
    getDoctorAvailability(selectedDoctorId, from, monthEnd, 500)
      .then((result) => {
        if (!cancelled) {
          setSlots(result ?? []);
        }
      })
      .catch((requestError) => {
        if (!cancelled) {
          setSlots([]);
          setSlotsError(formatApiError(requestError));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setSlotsLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [month, refreshKey, selectedDoctorId, view]);

  useEffect(() => {
    if (!user) {
      return;
    }
    const dual = isDualRole(user.roles) && !user.roles.includes("ADMIN");
    const doctorMode =
      user.roles.includes("DOCTOR") && (!dual || roleMode === "doctor");
    if (doctorMode && view !== "mine") {
      setView("mine");
    }
  }, [roleMode, user, view]);

  if (!readSession()) {
    return <Navigate to="/login" replace />;
  }

  const hasPatientRole = user?.roles.includes("PATIENT") ?? false;
  const hasDoctorRole = user?.roles.includes("DOCTOR") ?? false;
  const isAdmin = user?.roles.includes("ADMIN") ?? false;
  const dual = isDualRole(user?.roles) && !isAdmin;
  const isPatient = hasPatientRole && (!dual || roleMode === "patient");
  const isDoctor = hasDoctorRole && (!dual || roleMode === "doctor");
  const doctorsById = new Map(doctors.map((doctor) => [doctor.userId, doctor]));
  const selectedDoctor = doctorsById.get(selectedDoctorId);
  const todayIso = toIsoDate(new Date());

  function resolveDoctorName(doctorId: string): string {
    const doctor = doctorsById.get(doctorId);
    return doctor ? personName(doctor) : doctorId.slice(0, 8);
  }

  function resolvePatientName(patientId: string): string {
    return patientNames[patientId] ?? patientId.slice(0, 8);
  }

  const appointmentsByDay = groupAppointmentsByDay(appointments);
  const slotsByDay = groupSlotsByDay(slots);
  const selectedDayAppointments = appointmentsByDay.get(selectedDate) ?? [];
  const selectedDaySlots = slotsByDay.get(selectedDate) ?? [];
  const bookableFrom = Date.now() + 60 * 60 * 1000;
  const freeSlots = selectedDaySlots
    .filter(
      (slot) =>
        slot.status.toUpperCase() === "AVAILABLE" &&
        Date.parse(slot.startsAt) >= bookableFrom,
    )
    .sort((left, right) => Date.parse(left.startsAt) - Date.parse(right.startsAt));
  const reservedSlots = selectedDaySlots
    .filter((slot) => {
      const status = slot.status.toUpperCase();
      return status === "HELD" || status === "CONFIRMED";
    })
    .sort((left, right) => Date.parse(left.startsAt) - Date.parse(right.startsAt));
  const isPastDate = selectedDate < todayIso;

  const cells = buildCalendarCells(month);

  function changeMonth(delta: number) {
    const next = addMonths(month, delta);
    setMonth(next);
    setSelectedDate(toIsoDate(next));
  }

  function goToday() {
    const now = new Date();
    setMonth(startOfMonth(now));
    setSelectedDate(toIsoDate(now));
  }

  function onBooked(appointment: Appointment) {
    const moment = new Intl.DateTimeFormat("ru-RU", {
      dateStyle: "long",
      timeStyle: "short",
    }).format(new Date(appointment.starts_at));
    setBookingSlot(null);
    showToast({
      description: `Запись создана: ${resolveDoctorName(appointment.doctor_id)}, ${moment}.`,
      variant: "success",
    });
    reload();
  }

  return (
    <div className="min-h-screen bg-background">
      <AppHeader />

      <main className="mx-auto max-w-6xl space-y-6 px-6 py-10">
        <div className="space-y-3">
          <div className="space-y-1">
            {dual ? (
              <RoleModeSwitch
                mode={roleMode}
                onChange={setRoleMode}
                className="mb-2"
              />
            ) : null}
            <h1 className="text-2xl font-semibold">
              {isDoctor ? "Календарь приёмов" : "Календарь"}
            </h1>
            <p className="text-sm text-muted-foreground">
              {isDoctor
                ? "Записи пациентов к вам по датам."
                : view === "mine"
                  ? "Все ваши записи по датам."
                  : "Свободное время врача и запись онлайн."}
            </p>
          </div>
          {!isDoctor ? (
            <div className="inline-flex rounded-lg border bg-secondary/40 p-1">
              <button
                type="button"
                onClick={() => setView("mine")}
                className={cn(
                  "rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
                  view === "mine"
                    ? "bg-card shadow-sm"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                Мои записи
              </button>
              <button
                type="button"
                onClick={() => setView("doctor")}
                className={cn(
                  "rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
                  view === "doctor"
                    ? "bg-card shadow-sm"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                Записаться к врачу
              </button>
            </div>
          ) : null}
        </div>

        {loading ? (
          <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" />
            Загружаем календарь
          </div>
        ) : !isPatient && !isDoctor ? (
          <Alert>
            <AlertCircle className="h-4 w-4" />
            <AlertTitle>Календарь недоступен</AlertTitle>
            <AlertDescription>
              Календарь доступен пациентам и врачам.
            </AlertDescription>
          </Alert>
        ) : (
          <>
            <div className="flex flex-wrap items-end justify-between gap-3">
              {isDoctor ? (
                <p className="text-sm text-muted-foreground">
                  Записи пациентов, которые к вам записались.
                </p>
              ) : view === "doctor" ? (
                <div className="w-full space-y-1.5 sm:w-80">
                  <Label htmlFor="calendar-doctor">Врач</Label>
                  <Select
                    id="calendar-doctor"
                    value={selectedDoctorId}
                    onChange={(event) => setSelectedDoctorId(event.target.value)}
                  >
                    {doctors.map((doctor) => (
                      <option key={doctor.userId} value={doctor.userId}>
                        {personName(doctor)} — {doctor.specialization}
                      </option>
                    ))}
                  </Select>
                  <p className="text-xs text-muted-foreground">
                    Приём: Пн–Пт, 09:00–18:00. Запись доступна на любую неделю.
                  </p>
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">
                  Показаны только ваши записи.
                </p>
              )}

              <div className="flex flex-wrap items-center gap-2">
                <Button variant="outline" size="icon" onClick={() => changeMonth(-1)} aria-label="Предыдущий месяц">
                  <ChevronLeft className="h-4 w-4" />
                </Button>
                <Button variant="outline" size="sm" onClick={goToday}>
                  Сегодня
                </Button>
                <Button variant="outline" size="icon" onClick={() => changeMonth(1)} aria-label="Следующий месяц">
                  <ChevronRight className="h-4 w-4" />
                </Button>
                <Button variant="outline" size="icon" onClick={reload} aria-label="Обновить">
                  <RefreshCw className={cn("h-4 w-4", loading || slotsLoading ? "animate-spin" : null)} />
                </Button>
              </div>
            </div>

            {error ? (
              <Alert variant="destructive">
                <AlertCircle className="h-4 w-4" />
                <AlertTitle>Не удалось загрузить календарь</AlertTitle>
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            ) : null}

            <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
              <Card>
                <CardHeader className="space-y-2">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <CardTitle className="text-lg">{formatMonthTitle(month)}</CardTitle>
                    <div className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                      <span className="flex items-center gap-1.5">
                        <span className="h-2 w-2 rounded-full bg-primary" />{" "}
                        {isDoctor
                          ? "приёмы"
                          : view === "mine"
                            ? "ваши записи"
                            : "моя запись"}
                      </span>
                      {view === "doctor" ? (
                        <>
                          <span className="flex items-center gap-1.5">
                            <span className="h-2 w-2 rounded-full bg-emerald-500" /> свободно
                          </span>
                          <span className="flex items-center gap-1.5">
                            <span className="h-2 w-2 rounded-full bg-amber-500" /> занято
                          </span>
                        </>
                      ) : null}
                    </div>
                  </div>
                  <CardDescription>
                    {isDoctor
                      ? "Записи пациентов к вам"
                      : view === "mine"
                        ? "Ваши записи по датам"
                        : selectedDoctor
                          ? `${personName(selectedDoctor)} — ${selectedDoctor.specialization}`
                          : "Выберите врача"}
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="grid grid-cols-7 gap-1 pb-2 text-center text-xs font-medium text-muted-foreground">
                    {["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"].map((day) => (
                      <span key={day}>{day}</span>
                    ))}
                  </div>
                  <div className="grid grid-cols-7 gap-1">
                    {cells.map((date, index) => {
                      if (!date) {
                        return <div key={`empty-${index}`} className="min-h-24 rounded-lg" />;
                      }
                      const iso = toIsoDate(date);
                      const myAppointments = appointmentsByDay.get(iso) ?? [];
                      const daySlots = slotsByDay.get(iso) ?? [];
                      const free = daySlots.filter(
                        (slot) =>
                          slot.status.toUpperCase() === "AVAILABLE" &&
                          Date.parse(slot.startsAt) >= bookableFrom,
                      ).length;
                      const reserved = daySlots.filter((slot) => {
                        const status = slot.status.toUpperCase();
                        return status === "HELD" || status === "CONFIRMED";
                      }).length;
                      const selected = iso === selectedDate;
                      const isToday = iso === todayIso;
                      const isPast = iso < todayIso;

                      return (
                        <button
                          key={iso}
                          type="button"
                          onClick={() => setSelectedDate(iso)}
                          className={cn(
                            "flex min-h-24 flex-col rounded-lg border p-2 text-left transition-colors",
                            selected
                              ? "border-primary bg-primary/5"
                              : "hover:bg-accent",
                            isPast ? "opacity-60" : null,
                          )}
                        >
                          <span
                            className={cn(
                              "inline-flex h-6 w-6 items-center justify-center rounded-full text-sm font-medium",
                              isToday ? "bg-primary text-primary-foreground" : null,
                            )}
                          >
                            {date.getDate()}
                          </span>
                          <div className="mt-1 space-y-0.5">
                            {myAppointments.length > 0 ? (
                              <span className="flex items-center gap-1 text-[11px] font-medium text-primary">
                                <span className="h-1.5 w-1.5 rounded-full bg-primary" />
                                {isDoctor
                                  ? formatDoctorAppointmentsCount(myAppointments.length)
                                  : myAppointments.length === 1
                                    ? "Моя запись"
                                    : `${myAppointments.length} записи`}
                              </span>
                            ) : null}
                            {free > 0 ? (
                              <span className="block text-[11px] text-emerald-700">
                                {free} свободно
                              </span>
                            ) : null}
                            {reserved > 0 ? (
                              <span className="block text-[11px] text-amber-700">
                                {reserved} занято
                              </span>
                            ) : null}
                          </div>
                        </button>
                      );
                    })}
                  </div>
                  {slotsError ? (
                    <p className="mt-3 text-xs text-destructive">{slotsError}</p>
                  ) : slotsLoading ? (
                    <div className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      Обновляем доступность врача
                    </div>
                  ) : null}
                </CardContent>
              </Card>

              <Card className="h-fit">
                <CardHeader className="space-y-1">
                  <CardTitle className="text-base">{formatSelectedDate(selectedDate)}</CardTitle>
                  <CardDescription>
                    {isDoctor
                      ? "Записи пациентов к вам"
                      : view === "mine"
                        ? "Ваши записи"
                        : selectedDoctor
                          ? personName(selectedDoctor)
                          : "Врач не выбран"}
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-5">
                  <section className="space-y-2">
                    <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                      {isDoctor ? "Записи ко мне" : "Мои записи"}
                    </p>
                    {selectedDayAppointments.length === 0 ? (
                      <p className="text-sm text-muted-foreground">На эту дату записей нет.</p>
                    ) : (
                      <div className="space-y-2">
                        {selectedDayAppointments
                          .slice()
                          .sort(
                            (left, right) =>
                              Date.parse(left.starts_at) - Date.parse(right.starts_at),
                          )
                          .map((appointment) => (
                            <div
                              key={appointment.id}
                              className="rounded-lg border bg-background px-3 py-2 text-sm"
                            >
                              <div className="flex items-center justify-between gap-2">
                                <span className="flex items-center gap-1.5 font-medium">
                                  <Clock3 className="h-3.5 w-3.5 text-muted-foreground" />
                                  {formatTime(new Date(appointment.starts_at))}
                                </span>
                                <span className="rounded-full bg-secondary px-2 py-0.5 text-[11px] text-secondary-foreground">
                                  {STATUS_LABELS[appointment.status] ?? appointment.status}
                                </span>
                              </div>
                              <p className="mt-1 text-xs text-muted-foreground">
                                {isDoctor
                                  ? resolvePatientName(appointment.patient_id)
                                  : resolveDoctorName(appointment.doctor_id)}{" "}
                                · {appointment.reason}
                              </p>
                            </div>
                          ))}
                      </div>
                    )}
                  </section>

                  {isDoctor ? null : view === "doctor" ? (
                    <>
                      <section className="space-y-2">
                        <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                          Свободное время
                        </p>
                        {isPastDate ? (
                          <p className="text-sm text-muted-foreground">Дата уже прошла.</p>
                        ) : freeSlots.length === 0 ? (
                          <p className="text-sm text-muted-foreground">
                            На эту дату нет свободного времени.
                          </p>
                        ) : (
                          <div className="grid grid-cols-3 gap-1.5">
                            {freeSlots.map((slot) => (
                              <Button
                                key={`${slot.doctorId}-${slot.startsAt}`}
                                size="sm"
                                variant="outline"
                                className="justify-center"
                                onClick={() =>
                                  setBookingSlot({
                                    doctorId: selectedDoctorId,
                                    startsAt: toDateTimeLocal(new Date(slot.startsAt)),
                                    label: formatTime(new Date(slot.startsAt)),
                                  })
                                }
                              >
                                {formatTime(new Date(slot.startsAt))}
                              </Button>
                            ))}
                          </div>
                        )}
                      </section>

                      {reservedSlots.length > 0 ? (
                        <section className="space-y-2">
                          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                            Занятое время
                          </p>
                          <div className="flex flex-wrap gap-1.5">
                            {reservedSlots.map((slot) => (
                              <span
                                key={`${slot.doctorId}-${slot.startsAt}`}
                                className="rounded-md border bg-muted px-2 py-1 text-xs text-muted-foreground"
                              >
                                {formatTime(new Date(slot.startsAt))} ·{" "}
                                {slot.status.toUpperCase() === "CONFIRMED"
                                  ? "подтверждено"
                                  : "занято"}
                              </span>
                            ))}
                          </div>
                        </section>
                      ) : null}
                    </>
                  ) : (
                    <Button
                      variant="outline"
                      className="w-full"
                      onClick={() => setView("doctor")}
                    >
                      Записаться к врачу
                    </Button>
                  )}
                </CardContent>
              </Card>
            </div>
          </>
        )}
      </main>

      {user && !isDoctor ? (
        <BookingDialog
          open={Boolean(bookingSlot)}
          onClose={() => setBookingSlot(null)}
          onCreated={onBooked}
          patientId={user.id}
          initialDoctorId={bookingSlot?.doctorId}
          initialStartsAt={bookingSlot?.startsAt}
          lockDoctor
          lockDuration
        />
      ) : null}
    </div>
  );
}

function startOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function endOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth() + 1, 0);
}

function addMonths(date: Date, delta: number): Date {
  return startOfMonth(new Date(date.getFullYear(), date.getMonth() + delta, 1));
}

function buildCalendarCells(month: Date): Array<Date | null> {
  const first = startOfMonth(month);
  const offset = (first.getDay() + 6) % 7;
  const days = endOfMonth(month).getDate();
  const cells: Array<Date | null> = [];
  for (let index = 0; index < offset; index += 1) {
    cells.push(null);
  }
  for (let day = 1; day <= days; day += 1) {
    cells.push(new Date(month.getFullYear(), month.getMonth(), day));
  }
  while (cells.length % 7 !== 0) {
    cells.push(null);
  }
  return cells;
}

function toIsoDate(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function toDateTimeLocal(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
    date.getDate(),
  )}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function formatDoctorAppointmentsCount(count: number): string {
  const mod10 = count % 10;
  const mod100 = count % 100;
  if (mod10 === 1 && mod100 !== 11) {
    return `${count} приём`;
  }
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
    return `${count} приёма`;
  }
  return `${count} приёмов`;
}

function formatMonthTitle(date: Date): string {
  const value = new Intl.DateTimeFormat("ru-RU", { month: "long", year: "numeric" }).format(
    date,
  );
  return value.charAt(0).toUpperCase() + value.slice(1);
}

function formatSelectedDate(iso: string): string {
  return new Intl.DateTimeFormat("ru-RU", {
    weekday: "long",
    day: "numeric",
    month: "long",
  }).format(new Date(`${iso}T00:00:00`));
}

function formatTime(date: Date): string {
  return new Intl.DateTimeFormat("ru-RU", {
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function groupAppointmentsByDay(
  appointments: Appointment[],
): Map<string, Appointment[]> {
  const result = new Map<string, Appointment[]>();
  appointments.forEach((appointment) => {
    const iso = toIsoDate(new Date(appointment.starts_at));
    const list = result.get(iso) ?? [];
    list.push(appointment);
    result.set(iso, list);
  });
  return result;
}

function groupSlotsByDay(slots: ScheduleSlot[]): Map<string, ScheduleSlot[]> {
  const result = new Map<string, ScheduleSlot[]>();
  slots.forEach((slot) => {
    const iso = toIsoDate(new Date(slot.startsAt));
    const list = result.get(iso) ?? [];
    list.push(slot);
    result.set(iso, list);
  });
  return result;
}
