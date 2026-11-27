import {
  Check,
  CheckCheck,
  Clock3,
  Loader2,
  MessageSquare,
  Stethoscope,
  UserRound,
  XCircle,
} from "lucide-react";

import type { Appointment, AppointmentStatus } from "@/entities/appointment/model/types";
import { personName } from "@/entities/user/model/display";
import type { DoctorProfile } from "@/entities/user/model/types";
import { Card, CardContent } from "@/shared/ui/card";
import { Button } from "@/shared/ui/button";
import { cn } from "@/shared/lib/cn";

const STATUS_META: Record<AppointmentStatus, { label: string; className: string }> = {
  requested: {
    label: "Ожидает подтверждения",
    className: "bg-amber-100 text-amber-800",
  },
  confirmed: {
    label: "Подтверждена",
    className: "bg-primary/10 text-primary",
  },
  cancelled: {
    label: "Отменена",
    className: "bg-muted text-muted-foreground",
  },
  completed: {
    label: "Завершена",
    className: "bg-secondary text-secondary-foreground",
  },
  no_show: {
    label: "Неявка",
    className: "bg-destructive/10 text-destructive",
  },
};

interface AppointmentsFeedProps {
  appointments: Appointment[];
  doctorsById: Map<string, DoctorProfile>;
  patientNames: Record<string, string>;
  showPatient: boolean;
  currentUserId: string;
  canManage: boolean;
  allowEarlyComplete?: boolean;
  actionId: string | null;
  onOpenChat: (appointment: Appointment) => void;
  onConfirm: (appointment: Appointment) => void;
  onComplete: (appointment: Appointment) => void;
  onCancel: (appointment: Appointment) => void;
}

export function AppointmentsFeed({
  appointments,
  doctorsById,
  patientNames,
  showPatient,
  currentUserId,
  canManage,
  allowEarlyComplete = false,
  actionId,
  onOpenChat,
  onConfirm,
  onComplete,
  onCancel,
}: AppointmentsFeedProps) {
  return (
    <div className="space-y-3">
      {appointments.map((appointment) => {
        const start = new Date(appointment.starts_at);
        const end = new Date(appointment.ends_at);
        const durationMinutes = Math.max(
          1,
          Math.round((end.getTime() - start.getTime()) / 60_000),
        );
        const doctor = doctorsById.get(appointment.doctor_id);
        const status = STATUS_META[appointment.status] ?? STATUS_META.requested;
        const patientLabel = patientNames[appointment.patient_id];
        const busy = actionId === appointment.id;
        const isParticipant =
          currentUserId === appointment.patient_id ||
          currentUserId === appointment.doctor_id;
        const isActive =
          appointment.status === "requested" || appointment.status === "confirmed";
        const canConfirm = canManage && appointment.status === "requested";
        const canComplete = canManage && appointment.status === "confirmed";
        const canCancel = canManage && isActive;
        const hasStarted = start.getTime() <= Date.now();

        return (
          <Card key={appointment.id}>
            <CardContent className="space-y-4 p-5">
              <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                <div className="flex items-start gap-4">
                  <div className="flex h-16 w-16 shrink-0 flex-col items-center justify-center rounded-lg bg-accent text-primary">
                    <span className="text-xs font-medium uppercase">
                      {formatMonth(start)}
                    </span>
                    <span className="text-2xl font-semibold leading-none">
                      {start.getDate()}
                    </span>
                  </div>

                  <div className="space-y-1">
                    <p className="flex flex-wrap items-center gap-x-3 gap-y-1 font-medium">
                      <span className="flex items-center gap-1.5">
                        <Clock3 className="h-4 w-4 text-muted-foreground" />
                        {formatTime(start)}–{formatTime(end)}
                      </span>
                      <span className="text-sm font-normal text-muted-foreground">
                        {durationMinutes} мин
                      </span>
                    </p>
                    <p className="flex items-center gap-1.5 text-sm">
                      <Stethoscope className="h-4 w-4 text-muted-foreground" />
                      <span className="font-medium">
                        {doctor ? personName(doctor) : "Врач"}
                      </span>
                      {doctor ? (
                        <span className="text-muted-foreground">· {doctor.specialization}</span>
                      ) : null}
                    </p>
                    {showPatient ? (
                      <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
                        <UserRound className="h-4 w-4" />
                        Пациент: {patientLabel ?? shortId(appointment.patient_id)}
                      </p>
                    ) : null}
                    <p className="pt-1 text-sm text-muted-foreground">{appointment.reason}</p>
                  </div>
                </div>

                <span
                  className={cn(
                    "inline-flex w-fit items-center rounded-full px-3 py-1 text-xs font-medium",
                    status.className,
                  )}
                >
                  {status.label}
                </span>
              </div>

              {isParticipant || canConfirm || canComplete || canCancel ? (
                <div className="flex flex-wrap items-center gap-2 border-t pt-3">
                  {canConfirm ? (
                    <Button size="sm" disabled={busy} onClick={() => onConfirm(appointment)}>
                      {busy ? (
                        <Loader2 className="h-4 w-4 animate-spin" />
                      ) : (
                        <Check className="h-4 w-4" />
                      )}
                      Подтвердить
                    </Button>
                  ) : null}
                  {canComplete ? (
                    <Button
                      size="sm"
                      disabled={busy || (!hasStarted && !allowEarlyComplete)}
                      title={
                        hasStarted
                          ? undefined
                          : allowEarlyComplete
                            ? "Завершить досрочно"
                            : "Завершить можно после начала приёма"
                      }
                      onClick={() => onComplete(appointment)}
                    >
                      {busy ? (
                        <Loader2 className="h-4 w-4 animate-spin" />
                      ) : (
                        <CheckCheck className="h-4 w-4" />
                      )}
                      Завершить
                    </Button>
                  ) : null}
                  {canCancel ? (
                    <Button
                      size="sm"
                      variant="outline"
                      className="border-destructive/30 text-destructive hover:bg-destructive/10 hover:text-destructive"
                      disabled={busy}
                      onClick={() => onCancel(appointment)}
                    >
                      <XCircle className="h-4 w-4" />
                      Отменить
                    </Button>
                  ) : null}
                  {canComplete && !hasStarted && !allowEarlyComplete ? (
                    <span className="text-xs text-muted-foreground">
                      Завершение доступно после начала приёма
                    </span>
                  ) : null}
                  {isParticipant ? (
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={busy}
                      onClick={() => onOpenChat(appointment)}
                    >
                      <MessageSquare className="h-4 w-4" />
                      Чат
                    </Button>
                  ) : null}
                </div>
              ) : null}
            </CardContent>
          </Card>
        );
      })}
    </div>
  );
}

function formatMonth(date: Date): string {
  return new Intl.DateTimeFormat("ru-RU", { month: "short" })
    .format(date)
    .replace(".", "");
}

function formatTime(date: Date): string {
  return new Intl.DateTimeFormat("ru-RU", {
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function shortId(value: string): string {
  return value.slice(0, 8);
}
