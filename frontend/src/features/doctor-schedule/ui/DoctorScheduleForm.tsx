import { AlertCircle, CalendarDays, Clock3, Loader2 } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";

import { getMyWorkingHours, updateMyWorkingHours } from "@/entities/schedule/api/schedule";
import type { WorkingHoursRule } from "@/entities/schedule/model/types";
import { formatApiError } from "@/shared/api/http";
import { cn } from "@/shared/lib/cn";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";
import { useToast } from "@/shared/ui/toast";

const WEEKDAYS = [
  { value: 1, label: "Понедельник" },
  { value: 2, label: "Вторник" },
  { value: 3, label: "Среда" },
  { value: 4, label: "Четверг" },
  { value: 5, label: "Пятница" },
] as const;

const DEFAULT_START = "09:00";
const DEFAULT_END = "18:00";
const TIMEZONE = "Europe/Moscow";
const SLOT_DURATION_MINUTES = 30;
const TIME_STEP_MINUTES = 30;

interface DoctorScheduleFormProps {
  canEdit: boolean;
}

export function DoctorScheduleForm({ canEdit }: DoctorScheduleFormProps) {
  const { showToast } = useToast();
  const [rules, setRules] = useState<WorkingHoursRule[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedDays, setSelectedDays] = useState<number[]>([1, 2, 3, 4, 5]);
  const [startTime, setStartTime] = useState(DEFAULT_START);
  const [endTime, setEndTime] = useState(DEFAULT_END);
  const [effectiveFrom, setEffectiveFrom] = useState(() => toIsoDate(new Date()));
  const [effectiveTo, setEffectiveTo] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    getMyWorkingHours()
      .then((result) => {
        if (cancelled) {
          return;
        }
        setRules(result);
        applyExistingRules(result);
      })
      .catch((requestError) => {
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
    
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function applyExistingRules(result: WorkingHoursRule[]) {
    const weekdayRules = result
      .filter((rule) => rule.dayOfWeek >= 1 && rule.dayOfWeek <= 5)
      .sort((left, right) => left.dayOfWeek - right.dayOfWeek);
    const first = weekdayRules[0];
    if (!first) {
      return;
    }

    setSelectedDays(Array.from(new Set(weekdayRules.map((rule) => rule.dayOfWeek))).sort());
    setStartTime(shortTime(first.startTime));
    setEndTime(shortTime(first.endTime));
    setEffectiveFrom(first.effectiveFrom);
    setEffectiveTo(first.effectiveTo ?? "");
  }

  function toggleDay(day: number) {
    setSelectedDays((current) =>
      current.includes(day)
        ? current.filter((value) => value !== day)
        : [...current, day].sort((left, right) => left - right),
    );
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);

    if (selectedDays.length === 0) {
      setError("Выберите хотя бы один рабочий день.");
      return;
    }
    if (!startTime || !endTime) {
      setError("Укажите время начала и окончания приёма.");
      return;
    }
    if (startTime >= endTime) {
      setError("Время начала должно быть раньше времени окончания.");
      return;
    }
    if (
      toMinutes(startTime) % TIME_STEP_MINUTES !== 0 ||
      toMinutes(endTime) % TIME_STEP_MINUTES !== 0
    ) {
      setError(`Время должно быть кратно ${TIME_STEP_MINUTES} минутам.`);
      return;
    }
    if (toMinutes(endTime) - toMinutes(startTime) < SLOT_DURATION_MINUTES) {
      setError("Интервал должен быть не короче одного приёма (30 мин).");
      return;
    }
    if (!effectiveFrom) {
      setError("Укажите дату, с которой начинает действовать график.");
      return;
    }
    if (effectiveTo && effectiveTo < effectiveFrom) {
      setError("Дата окончания не может быть раньше даты начала.");
      return;
    }

    setSubmitting(true);
    try {
      const savedRules = await updateMyWorkingHours({
        entries: selectedDays.map((day) => ({
          dayOfWeek: day,
          startTime: withSeconds(startTime),
          endTime: withSeconds(endTime),
          slotDurationMinutes: SLOT_DURATION_MINUTES,
          bufferMinutes: 0,
          timezone: TIMEZONE,
          slotType: "CONSULTATION",
          location: "ONLINE",
        })),
        effectiveFrom,
        effectiveTo: effectiveTo || null,
      });
      setRules(savedRules);
      showToast({ description: "График работы сохранён.", variant: "success" });
    } catch (requestError) {
      setError(formatApiError(requestError));
    } finally {
      setSubmitting(false);
    }
  }

  const currentSchedule = describeRules(rules);

  return (
    <form className="space-y-5" onSubmit={onSubmit} noValidate>
      {!canEdit ? (
        <Alert className="border-amber-200 bg-amber-50 text-amber-900">
          <Clock3 className="h-4 w-4" />
          <AlertDescription>
            Настроить график можно после подтверждения профиля администратором.
          </AlertDescription>
        </Alert>
      ) : null}

      {error ? (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : null}

      {loading ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          Загружаем график работы
        </div>
      ) : (
        <fieldset
          className={cn("space-y-5", (!canEdit || submitting) && "opacity-70")}
          disabled={!canEdit || submitting}
        >
          {currentSchedule ? (
            <div className="rounded-lg border bg-secondary/30 p-3 text-sm">
              <span className="font-medium">Текущий график: </span>
              <span className="text-muted-foreground">{currentSchedule}</span>
            </div>
          ) : null}

          <div className="space-y-2">
            <Label>Рабочие дни</Label>
            <div className="grid gap-2 sm:grid-cols-2">
              {WEEKDAYS.map((day) => {
                const selected = selectedDays.includes(day.value);
                return (
                  <label
                    key={day.value}
                    className={cn(
                      "flex cursor-pointer items-center gap-3 rounded-lg border px-3 py-2.5 text-sm transition-colors",
                      selected ? "border-primary bg-primary/5" : "hover:bg-accent",
                    )}
                  >
                    <input
                      type="checkbox"
                      className="h-4 w-4 accent-primary"
                      checked={selected}
                      onChange={() => toggleDay(day.value)}
                    />
                    <span className="font-medium">{day.label}</span>
                  </label>
                );
              })}
            </div>
            <p className="text-xs text-muted-foreground">
              Отметьте дни, в которые принимаете пациентов.
            </p>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="schedule-start">Начало приёма</Label>
              <Input
                id="schedule-start"
                type="time"
                step={TIME_STEP_MINUTES * 60}
                value={startTime}
                onChange={(event) => {
                  setStartTime(event.target.value);
                }}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="schedule-end">Окончание приёма</Label>
              <Input
                id="schedule-end"
                type="time"
                step={TIME_STEP_MINUTES * 60}
                value={endTime}
                onChange={(event) => {
                  setEndTime(event.target.value);
                }}
              />
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="schedule-from">График действует с</Label>
              <Input
                id="schedule-from"
                type="date"
                value={effectiveFrom}
                onChange={(event) => {
                  setEffectiveFrom(event.target.value);
                }}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="schedule-to">по</Label>
              <Input
                id="schedule-to"
                type="date"
                min={effectiveFrom || undefined}
                value={effectiveTo}
                onChange={(event) => {
                  setEffectiveTo(event.target.value);
                }}
              />
            </div>
          </div>

          <p className="flex items-start gap-1.5 text-xs text-muted-foreground">
            <CalendarDays className="mt-0.5 h-3.5 w-3.5 shrink-0" />
            <span>
              Если дата окончания не указана, график действует бессрочно. Время указано в часовом
              поясе {TIMEZONE}, длительность приёма — {SLOT_DURATION_MINUTES} мин. Сохранение
              заменит ранее заданный график начиная с выбранной даты.
            </span>
          </p>

          <Button type="submit" disabled={submitting}>
            {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            Сохранить график
          </Button>
        </fieldset>
      )}
    </form>
  );
}

function describeRules(rules: WorkingHoursRule[]): string | null {
  const weekdayRules = rules
    .filter((rule) => rule.dayOfWeek >= 1 && rule.dayOfWeek <= 5)
    .sort((left, right) => left.dayOfWeek - right.dayOfWeek);
  const first = weekdayRules[0];
  if (!first) {
    return null;
  }

  const days = Array.from(new Set(weekdayRules.map((rule) => rule.dayOfWeek)))
    .map((day) => WEEKDAYS.find((item) => item.value === day)?.label ?? String(day))
    .join(", ");
  const period = first.effectiveTo
    ? `${formatDate(first.effectiveFrom)} — ${formatDate(first.effectiveTo)}`
    : `с ${formatDate(first.effectiveFrom)}, бессрочно`;

  return `${days}: ${shortTime(first.startTime)}–${shortTime(first.endTime)}, ${period}`;
}

function shortTime(value: string): string {
  return value.slice(0, 5);
}

function withSeconds(value: string): string {
  return value.length === 5 ? `${value}:00` : value;
}

function toMinutes(value: string): number {
  const [hours, minutes] = value.split(":").map(Number);
  return hours * 60 + minutes;
}

function toIsoDate(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function formatDate(iso: string): string {
  const date = new Date(`${iso}T00:00:00`);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  return new Intl.DateTimeFormat("ru-RU", { dateStyle: "medium" }).format(date);
}
