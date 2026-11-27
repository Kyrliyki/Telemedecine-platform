import {
  Activity,
  CalendarDays,
  Download,
  FileText,
  Loader2,
  Paperclip,
  Pill,
} from "lucide-react";
import { useState, type ReactNode } from "react";

import type {
  MedicalAttachment,
  MedicalRecord,
  MedicalRecordStatus,
} from "@/entities/emr/model/types";
import { getChatFile } from "@/entities/file/api/files";
import { formatApiError } from "@/shared/api/http";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/ui/card";
import { cn } from "@/shared/lib/cn";

const STATUS_META: Record<MedicalRecordStatus, { label: string; className: string }> = {
  DRAFT: { label: "Черновик", className: "bg-amber-100 text-amber-800" },
  FINALIZED: { label: "Завершена", className: "bg-primary/10 text-primary" },
  AMENDED: { label: "Изменена", className: "bg-secondary text-secondary-foreground" },
  DELETED: { label: "Удалена", className: "bg-muted text-muted-foreground" },
};

interface MedicalRecordCardProps {
  record: MedicalRecord;
  doctorName?: string;
}

export function MedicalRecordCard({ record, doctorName }: MedicalRecordCardProps) {
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const [attachmentError, setAttachmentError] = useState<string | null>(null);
  const status = STATUS_META[record.status] ?? STATUS_META.DRAFT;
  const vitals = record.vitalSigns;

  async function openAttachment(attachment: MedicalAttachment) {
    setDownloadingId(attachment.id);
    setAttachmentError(null);
    try {
      const file = await getChatFile(attachment.fileId);
      if (!file.download_url) {
        setAttachmentError("Файл ещё не готов к скачиванию.");
        return;
      }
      window.open(file.download_url, "_blank", "noopener,noreferrer");
    } catch (error) {
      setAttachmentError(formatApiError(error));
    } finally {
      setDownloadingId(null);
    }
  }

  return (
    <Card>
      <CardHeader className="space-y-1">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <CardTitle className="flex items-center gap-2 text-lg">
            <CalendarDays className="h-4 w-4 text-primary" />
            {formatDateTime(record.createdAt)}
          </CardTitle>
          <span
            className={cn(
              "inline-flex rounded-full px-3 py-1 text-xs font-medium",
              status.className,
            )}
          >
            {status.label}
          </span>
        </div>
        <CardDescription>
          {doctorName ? `Врач: ${doctorName}` : "Медицинская запись"}
          {record.finalizedAt ? ` · подписана ${formatDateTime(record.finalizedAt)}` : ""}
        </CardDescription>
      </CardHeader>

      <CardContent className="space-y-5">
        {record.chiefComplaint ? (
          <Section title="Жалобы" text={record.chiefComplaint} />
        ) : null}

        {record.diagnosisCode || record.diagnosisText ? (
          <div className="space-y-1">
            <SectionTitle>Диагноз</SectionTitle>
            <p className="text-sm">
              {record.diagnosisCode ? (
                <span className="mr-2 rounded bg-secondary px-2 py-0.5 font-mono text-xs">
                  {record.diagnosisCode}
                </span>
              ) : null}
              {record.diagnosisText ?? "—"}
            </p>
          </div>
        ) : null}

        {record.notes ? <Section title="Заключение" text={record.notes} /> : null}
        {record.treatmentPlan ? (
          <Section title="План лечения" text={record.treatmentPlan} />
        ) : null}

        {vitals ? (
          <div className="space-y-2">
            <SectionTitle>
              <Activity className="mr-1 inline h-3.5 w-3.5" />
              Показатели
            </SectionTitle>
            <dl className="grid grid-cols-2 gap-2 sm:grid-cols-3">
              <Vital label="Рост" value={vitals.heightCm} unit="см" />
              <Vital label="Вес" value={vitals.weightKg} unit="кг" />
              <Vital
                label="Давление"
                value={
                  vitals.systolic && vitals.diastolic
                    ? `${vitals.systolic}/${vitals.diastolic}`
                    : null
                }
                unit="мм рт. ст."
              />
              <Vital label="Пульс" value={vitals.pulse} unit="уд/мин" />
              <Vital label="Температура" value={vitals.temperatureC} unit="°C" />
              <Vital label="SpO₂" value={vitals.spo2} unit="%" />
            </dl>
          </div>
        ) : null}

        {record.prescriptions.length > 0 ? (
          <div className="space-y-2">
            <SectionTitle>
              <Pill className="mr-1 inline h-3.5 w-3.5" />
              Назначения
            </SectionTitle>
            <div className="space-y-2">
              {record.prescriptions.map((prescription) => (
                <div
                  key={prescription.id}
                  className="rounded-lg border bg-background px-3 py-2 text-sm"
                >
                  <div className="flex flex-wrap items-baseline justify-between gap-2">
                    <span className="font-medium">{prescription.medicationName}</span>
                    <span className="text-xs text-muted-foreground">
                      {prescription.dosage} · {prescription.frequency} ·{" "}
                      {prescription.durationDays} дн.
                    </span>
                  </div>
                  {prescription.instructions ? (
                    <p className="mt-1 text-xs text-muted-foreground">
                      {prescription.instructions}
                    </p>
                  ) : null}
                </div>
              ))}
            </div>
          </div>
        ) : null}

        {record.attachments.length > 0 ? (
          <div className="space-y-2">
            <SectionTitle>
              <Paperclip className="mr-1 inline h-3.5 w-3.5" />
              Вложения
            </SectionTitle>
            <div className="space-y-2">
              {record.attachments.map((attachment) => (
                <div
                  key={attachment.id}
                  className="flex items-center justify-between gap-2 rounded-lg border bg-background px-3 py-2 text-sm"
                >
                  <div className="flex min-w-0 items-center gap-2">
                    <FileText className="h-4 w-4 shrink-0 text-muted-foreground" />
                    <span className="truncate">
                      {attachment.description || "Медицинский документ"}
                    </span>
                  </div>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={downloadingId === attachment.id}
                    onClick={() => void openAttachment(attachment)}
                  >
                    {downloadingId === attachment.id ? (
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    ) : (
                      <Download className="h-3.5 w-3.5" />
                    )}
                    Скачать
                  </Button>
                </div>
              ))}
            </div>
            {attachmentError ? (
              <p className="text-xs text-destructive">{attachmentError}</p>
            ) : null}
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

function Section({ title, text }: { title: string; text: string }) {
  return (
    <div className="space-y-1">
      <SectionTitle>{title}</SectionTitle>
      <p className="whitespace-pre-wrap text-sm">{text}</p>
    </div>
  );
}

function SectionTitle({ children }: { children: ReactNode }) {
  return (
    <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
      {children}
    </p>
  );
}

function Vital({
  label,
  value,
  unit,
}: {
  label: string;
  value: number | string | null;
  unit: string;
}) {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  return (
    <div className="rounded-lg border bg-background px-3 py-2">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="text-sm font-medium">
        {value} <span className="text-xs font-normal text-muted-foreground">{unit}</span>
      </dd>
    </div>
  );
}

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("ru-RU", {
    dateStyle: "long",
    timeStyle: "short",
  }).format(date);
}
