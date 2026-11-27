import { AlertCircle, CalendarClock, Loader2 } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";

import { createAppointment } from "@/entities/appointment/api/appointments";
import type { Appointment } from "@/entities/appointment/model/types";
import { listDoctors } from "@/entities/user/api/userApi";
import { personName } from "@/entities/user/model/display";
import type { DoctorProfile } from "@/entities/user/model/types";
import { formatApiError } from "@/shared/api/http";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Dialog } from "@/shared/ui/dialog";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";
import { Select } from "@/shared/ui/select";
import { Textarea } from "@/shared/ui/textarea";

const DURATIONS = [30, 45, 60] as const;
const LEAD_TIME_MS = 60 * 60 * 1000;
const STEP_MINUTES = 30;
const WORKDAY_START_MINUTES = 9 * 60;
const WORKDAY_END_MINUTES = 18 * 60;

interface BookingDialogProps {
  open: boolean;
  onClose: () => void;
  onCreated: (appointment: Appointment) => void;
  patientId: string;
  initialDoctorId?: string;
  initialStartsAt?: string;
  lockDoctor?: boolean;
  lockDuration?: boolean;
}

export function BookingDialog({
  open,
  onClose,
  onCreated,
  patientId,
  initialDoctorId,
  initialStartsAt,
  lockDoctor = false,
  lockDuration = false,
}: BookingDialogProps) {
  const [doctors, setDoctors] = useState<DoctorProfile[]>([]);
  const [doctorsLoading, setDoctorsLoading] = useState(false);
  const [doctorsError, setDoctorsError] = useState<string | null>(null);

  const [doctorId, setDoctorId] = useState("");
  const [startsAt, setStartsAt] = useState("");
  const [duration, setDuration] = useState<number>(30);
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const minStartsAt = toLocalInputValue(roundUpToStep(new Date(Date.now() + LEAD_TIME_MS)));

  useEffect(() => {
    if (!open) {
      return;
    }
    setError(null);
    setSubmitting(false);
    setDoctorId(initialDoctorId ?? "");
    setDuration(30);
    setReason("");
    setStartsAt(initialStartsAt ?? defaultStartsAt());
  }, [initialDoctorId, initialStartsAt, open]);

  useEffect(() => {
    if (!open) {
      return;
    }
    let cancelled = false;
    setDoctorsLoading(true);
    setDoctorsError(null);
    listDoctors({ page: 0, size: 100 })
      .then((result) => {
        if (!cancelled) {
          setDoctors(result.content ?? []);
        }
      })
      .catch((requestError) => {
        if (!cancelled) {
          setDoctorsError(formatApiError(requestError));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setDoctorsLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [open]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);

    const start = new Date(startsAt);
    if (!doctorId) {
      setError("Выберите врача.");
      return;
    }
    if (!startsAt || Number.isNaN(start.getTime())) {
      setError("Выберите дату и время приёма.");
      return;
    }
    if (start.getMinutes() % STEP_MINUTES !== 0) {
      setError(`Выберите время с шагом ${STEP_MINUTES} минут.`);
      return;
    }
    const doctorTime = getZonedTime(start, "Europe/Moscow");
    if (doctorTime.weekday === "Sat" || doctorTime.weekday === "Sun") {
      setError("Приём проводится по будням (Пн–Пт).");
      return;
    }
    if (
      doctorTime.minutes < WORKDAY_START_MINUTES ||
      doctorTime.minutes > WORKDAY_END_MINUTES - 30
    ) {
      setError("Выберите время в рабочие часы врача: 09:00–18:00.");
      return;
    }
    if (start.getTime() < Date.now() + LEAD_TIME_MS) {
      setError("Записаться можно не позднее чем за час до приёма.");
      return;
    }
    if (reason.trim().length < 3) {
      setError("Опишите причину обращения (минимум 3 символа).");
      return;
    }

    setSubmitting(true);
    try {
      const created = await createAppointment({
        patient_id: patientId,
        doctor_id: doctorId,
        starts_at: start.toISOString(),
        ends_at: new Date(start.getTime() + duration * 60_000).toISOString(),
        reason: reason.trim(),
      });
      onCreated(created);
    } catch (requestError) {
      setError(formatApiError(requestError));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Запись на приём"
      description="Выберите врача и удобное время — заявка сразу появится в списке."
    >
      <form className="space-y-4" onSubmit={onSubmit}>
        <div className="space-y-1.5">
          <Label htmlFor="booking-doctor">Врач</Label>
          {doctorsLoading ? (
            <div className="flex h-10 items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" />
              Загружаем врачей
            </div>
          ) : (
            <Select
              id="booking-doctor"
              value={doctorId}
              onChange={(event) => setDoctorId(event.target.value)}
              disabled={doctors.length === 0 || lockDoctor}
              required
            >
              <option value="" disabled>
                Выберите врача
              </option>
              {doctors.map((doctor) => (
                <option key={doctor.userId} value={doctor.userId}>
                  {personName(doctor)} — {doctor.specialization}
                </option>
              ))}
            </Select>
          )}
          {doctorsError ? (
            <p className="text-xs text-destructive">{doctorsError}</p>
          ) : null}
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label htmlFor="booking-starts-at">Дата и время</Label>
            <Input
              id="booking-starts-at"
              type="datetime-local"
              value={startsAt}
              min={minStartsAt}
              step={STEP_MINUTES * 60}
              onChange={(event) => setStartsAt(event.target.value)}
              className="[color-scheme:light]"
              required
            />
            <p className="text-xs text-muted-foreground">Пн–Пт, 09:00–18:00</p>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="booking-duration">Длительность</Label>
            <Select
              id="booking-duration"
              value={duration}
              onChange={(event) => setDuration(Number(event.target.value))}
              disabled={lockDuration}
            >
              {DURATIONS.map((value) => (
                <option key={value} value={value}>
                  {value} минут
                </option>
              ))}
            </Select>
          </div>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="booking-reason">Причина обращения</Label>
          <Textarea
            id="booking-reason"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder="Например: консультация по результатам анализов"
            maxLength={500}
            required
          />
        </div>

        {error ? (
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}

        <div className="space-y-2 pt-1">
          <Button type="submit" className="w-full" disabled={submitting || doctorsLoading}>
            {submitting ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <CalendarClock className="h-4 w-4" />
            )}
            Записаться
          </Button>
          <Button type="button" variant="ghost" className="w-full" onClick={onClose}>
            Отмена
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

function getZonedTime(
  date: Date,
  timeZone: string,
): { minutes: number; weekday: string } {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone,
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date);
  const get = (type: string) => parts.find((part) => part.type === type)?.value ?? "";
  return {
    minutes: Number(get("hour")) * 60 + Number(get("minute")),
    weekday: get("weekday"),
  };
}

function roundUpToStep(date: Date): Date {
  const result = new Date(date);
  result.setSeconds(0, 0);
  const remainder = result.getMinutes() % STEP_MINUTES;
  if (remainder !== 0) {
    result.setMinutes(result.getMinutes() + STEP_MINUTES - remainder);
  }
  return result;
}

function defaultStartsAt(): string {
  const date = new Date();
  date.setDate(date.getDate() + 1);
  date.setHours(10, 0, 0, 0);
  return toLocalInputValue(date);
}

function toLocalInputValue(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`;
}
