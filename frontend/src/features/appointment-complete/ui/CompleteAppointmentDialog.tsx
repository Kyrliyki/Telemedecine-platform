import { AlertCircle, CheckCircle2, Loader2 } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";

import { completeAppointment } from "@/entities/appointment/api/appointments";
import type { Appointment } from "@/entities/appointment/model/types";
import {
  createMedicalRecord,
  finalizeMedicalRecord,
  updateMedicalRecord,
  upsertMedicalVitals,
  type VitalSignsPayload,
} from "@/entities/emr/api/emr";
import type { MedicalRecord } from "@/entities/emr/model/types";
import { formatApiError } from "@/shared/api/http";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Dialog } from "@/shared/ui/dialog";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";
import { Textarea } from "@/shared/ui/textarea";

interface CompleteAppointmentDialogProps {
  open: boolean;
  appointment: Appointment | null;
  isAdmin: boolean;
  onClose: () => void;
  onCompleted: (record: MedicalRecord) => void;
}

interface VitalsForm {
  heightCm: string;
  weightKg: string;
  systolic: string;
  diastolic: string;
  pulse: string;
  temperatureC: string;
  spo2: string;
}

const EMPTY_VITALS: VitalsForm = {
  heightCm: "",
  weightKg: "",
  systolic: "",
  diastolic: "",
  pulse: "",
  temperatureC: "",
  spo2: "",
};

export function CompleteAppointmentDialog({
  open,
  appointment,
  isAdmin,
  onClose,
  onCompleted,
}: CompleteAppointmentDialogProps) {
  const [chiefComplaint, setChiefComplaint] = useState("");
  const [diagnosisCode, setDiagnosisCode] = useState("");
  const [diagnosisText, setDiagnosisText] = useState("");
  const [notes, setNotes] = useState("");
  const [treatmentPlan, setTreatmentPlan] = useState("");
  const [vitals, setVitals] = useState<VitalsForm>(EMPTY_VITALS);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    setChiefComplaint("");
    setDiagnosisCode("");
    setDiagnosisText("");
    setNotes("");
    setTreatmentPlan("");
    setVitals(EMPTY_VITALS);
    setSubmitting(false);
    setError(null);
  }, [open, appointment?.id]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!appointment) {
      return;
    }
    if (chiefComplaint.trim().length < 3) {
      setError("Опишите жалобы пациента (минимум 3 символа).");
      return;
    }
    if (diagnosisText.trim().length < 3) {
      setError("Укажите диагноз (минимум 3 символа).");
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      let record = await createMedicalRecord({
        patientId: appointment.patient_id,
        appointmentId: appointment.id,
        doctorId: isAdmin ? appointment.doctor_id : undefined,
        chiefComplaint: chiefComplaint.trim(),
        diagnosisCode: diagnosisCode.trim() || undefined,
        diagnosisText: diagnosisText.trim(),
        notes: notes.trim() || undefined,
        treatmentPlan: treatmentPlan.trim() || undefined,
      });

      if (record.status === "DRAFT" || record.status === "AMENDED") {
        record = await updateMedicalRecord(record.id, {
          chiefComplaint: chiefComplaint.trim(),
          diagnosisCode: diagnosisCode.trim() || undefined,
          diagnosisText: diagnosisText.trim(),
          notes: notes.trim() || undefined,
          treatmentPlan: treatmentPlan.trim() || undefined,
        });

        const vitalsPayload = buildVitals(vitals);
        if (Object.keys(vitalsPayload).length > 0) {
          record = await upsertMedicalVitals(record.id, vitalsPayload);
        }

        record = await finalizeMedicalRecord(record.id);
      }

      await completeAppointment(appointment.id);
      onCompleted(record);
    } catch (requestError) {
      setError(formatApiError(requestError));
    } finally {
      setSubmitting(false);
    }
  }

  if (!appointment) {
    return null;
  }

  const startsAt = new Date(appointment.starts_at);
  const started = startsAt.getTime() <= Date.now();

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Завершение приёма"
      description="Заполните запись в медкнижку. После сохранения приём будет завершён."
    >
      <form className="space-y-4" onSubmit={onSubmit}>
        <div className="rounded-lg border bg-secondary/40 px-3 py-2 text-sm">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="font-medium">
              {new Intl.DateTimeFormat("ru-RU", {
                dateStyle: "long",
                timeStyle: "short",
              }).format(startsAt)}
            </span>
            {!started && isAdmin ? (
              <span className="text-xs text-primary">Досрочное завершение</span>
            ) : null}
          </div>
          <p className="mt-1 text-xs text-muted-foreground">{appointment.reason}</p>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="complete-complaint">Жалобы</Label>
          <Textarea
            id="complete-complaint"
            value={chiefComplaint}
            onChange={(event) => setChiefComplaint(event.target.value)}
            placeholder="Что беспокоит пациента"
            required
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-[140px_1fr]">
          <div className="space-y-1.5">
            <Label htmlFor="complete-diagnosis-code">Код МКБ</Label>
            <Input
              id="complete-diagnosis-code"
              value={diagnosisCode}
              onChange={(event) => setDiagnosisCode(event.target.value)}
              placeholder="Например, I10"
              maxLength={32}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="complete-diagnosis-text">Диагноз</Label>
            <Input
              id="complete-diagnosis-text"
              value={diagnosisText}
              onChange={(event) => setDiagnosisText(event.target.value)}
              placeholder="Описание диагноза"
              required
            />
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label htmlFor="complete-notes">Заключение</Label>
            <Textarea
              id="complete-notes"
              value={notes}
              onChange={(event) => setNotes(event.target.value)}
              placeholder="Результаты осмотра, рекомендации"
              className="min-h-[88px]"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="complete-plan">План лечения</Label>
            <Textarea
              id="complete-plan"
              value={treatmentPlan}
              onChange={(event) => setTreatmentPlan(event.target.value)}
              placeholder="Терапия и дальнейшие шаги"
              className="min-h-[88px]"
            />
          </div>
        </div>

        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Показатели (необязательно)
          </p>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            <VitalInput
              label="Рост, см"
              value={vitals.heightCm}
              onChange={(value) => setVitals({ ...vitals, heightCm: value })}
            />
            <VitalInput
              label="Вес, кг"
              value={vitals.weightKg}
              onChange={(value) => setVitals({ ...vitals, weightKg: value })}
            />
            <VitalInput
              label="САД"
              value={vitals.systolic}
              onChange={(value) => setVitals({ ...vitals, systolic: value })}
            />
            <VitalInput
              label="ДАД"
              value={vitals.diastolic}
              onChange={(value) => setVitals({ ...vitals, diastolic: value })}
            />
            <VitalInput
              label="Пульс"
              value={vitals.pulse}
              onChange={(value) => setVitals({ ...vitals, pulse: value })}
            />
            <VitalInput
              label="Темп., °C"
              value={vitals.temperatureC}
              onChange={(value) => setVitals({ ...vitals, temperatureC: value })}
            />
            <VitalInput
              label="SpO₂, %"
              value={vitals.spo2}
              onChange={(value) => setVitals({ ...vitals, spo2: value })}
            />
          </div>
        </div>

        {error ? (
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}

        <div className="space-y-2 pt-1">
          <Button type="submit" className="w-full" disabled={submitting}>
            {submitting ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <CheckCircle2 className="h-4 w-4" />
            )}
            Завершить приём
          </Button>
          <Button type="button" variant="ghost" className="w-full" onClick={onClose}>
            Отмена
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

function VitalInput({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="space-y-1">
      <Label className="text-xs text-muted-foreground">{label}</Label>
      <Input
        type="number"
        inputMode="decimal"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="h-9"
      />
    </div>
  );
}

function buildVitals(form: VitalsForm): VitalSignsPayload {
  const payload: VitalSignsPayload = {};
  const assign = (key: keyof VitalSignsPayload, value: string) => {
    if (!value.trim()) {
      return;
    }
    const parsed = Number(value);
    if (Number.isFinite(parsed)) {
      payload[key] = parsed;
    }
  };
  assign("heightCm", form.heightCm);
  assign("weightKg", form.weightKg);
  assign("systolic", form.systolic);
  assign("diastolic", form.diastolic);
  assign("pulse", form.pulse);
  assign("temperatureC", form.temperatureC);
  assign("spo2", form.spo2);
  return payload;
}
