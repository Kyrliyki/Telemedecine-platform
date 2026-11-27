import {
  AlertCircle,
  CheckCircle2,
  Loader2,
  RefreshCw,
  ShieldCheck,
  ShieldX,
} from "lucide-react";
import { FormEvent, useCallback, useEffect, useState } from "react";

import {
  approveDoctor,
  listDoctorsForAdmin,
  rejectDoctor,
} from "@/entities/user/api/userApi";
import {
  formatDate,
  formatExperience,
  personName,
  verificationLabels,
} from "@/entities/user/model/display";
import type { DoctorProfile } from "@/entities/user/model/types";
import { formatApiError } from "@/shared/api/http";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/ui/card";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";
import { useToast } from "@/shared/ui/toast";

const PAGE_SIZE = 20;
const STATUS_OPTIONS = ["PENDING", "VERIFIED", "REJECTED"];

export function AdminDoctorsView() {
  const { showToast } = useToast();
  const [verificationStatus, setVerificationStatus] = useState("PENDING");
  const [specialization, setSpecialization] = useState("");
  const [applied, setApplied] = useState({ verificationStatus: "PENDING", specialization: "" });
  const [page, setPage] = useState(0);
  const [doctors, setDoctors] = useState<DoctorProfile[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyUserId, setBusyUserId] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setError(null);
      try {
        const result = await listDoctorsForAdmin({
          page,
          size: PAGE_SIZE,
          verificationStatus: applied.verificationStatus || undefined,
          specialization: applied.specialization.trim() || undefined,
        });
        if (!cancelled) {
          setDoctors(result.content ?? []);
          setTotalElements(result.totalElements ?? 0);
          setTotalPages(result.totalPages ?? 0);
        }
      } catch (requestError) {
        if (!cancelled) {
          setError(formatApiError(requestError));
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
  }, [applied, page, reloadKey]);

  const reload = useCallback(() => setReloadKey((value) => value + 1), []);

  function applyFilters(event: FormEvent) {
    event.preventDefault();
    setPage(0);
    setApplied({ verificationStatus, specialization });
  }

  function resetFilters() {
    setVerificationStatus("PENDING");
    setSpecialization("");
    setApplied({ verificationStatus: "PENDING", specialization: "" });
    setPage(0);
  }

  async function runAction(userId: string, message: string, action: () => Promise<unknown>) {
    setBusyUserId(userId);
    setError(null);
    try {
      await action();
      showToast({ description: message, variant: "success" });
      reload();
    } catch (requestError) {
      setError(formatApiError(requestError));
    } finally {
      setBusyUserId(null);
    }
  }

  function onApprove(doctor: DoctorProfile) {
    void runAction(
      doctor.userId,
      `Профиль ${personName(doctor)} подтверждён`,
      () => approveDoctor(doctor.userId),
    );
  }

  function onReject(doctor: DoctorProfile) {
    const verified = doctor.verificationStatus === "VERIFIED";
    const reason = window.prompt(
      verified
        ? `Отозвать верификацию врача ${personName(doctor)}? Укажите причину`
        : `Причина отклонения профиля ${personName(doctor)}`,
      verified ? "Верификация отозвана администратором" : "Документы не подтверждены",
    );
    if (reason === null) {
      return;
    }
    if (!reason.trim()) {
      setError("Необходимо указать причину отклонения.");
      return;
    }
    void runAction(
      doctor.userId,
      verified
        ? `Верификация ${personName(doctor)} отозвана`
        : `Профиль ${personName(doctor)} отклонён`,
      () => rejectDoctor(doctor.userId, reason.trim()),
    );
  }

  return (
    <Card>
      <CardHeader className="space-y-3">
        <div>
          <CardTitle className="flex items-center gap-2 text-xl">
            <ShieldCheck className="h-5 w-5 text-primary" />
            Проверка врачей
          </CardTitle>
          <CardDescription>
            Заявки на верификацию профилей. Всего: {totalElements}.
          </CardDescription>
        </div>

        <form className="grid gap-3 sm:grid-cols-[180px_1fr_auto]" onSubmit={applyFilters}>
          <div className="space-y-1">
            <Label htmlFor="doctor-verification" className="text-xs">
              Статус
            </Label>
            <select
              id="doctor-verification"
              className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
              value={verificationStatus}
              onChange={(event) => setVerificationStatus(event.target.value)}
            >
              <option value="">Любой</option>
              {STATUS_OPTIONS.map((status) => (
                <option key={status} value={status}>
                  {verificationLabels[status] ?? status}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1">
            <Label htmlFor="doctor-admin-specialization" className="text-xs">
              Специализация
            </Label>
            <Input
              id="doctor-admin-specialization"
              value={specialization}
              onChange={(event) => setSpecialization(event.target.value)}
              placeholder="Кардиолог"
            />
          </div>
          <div className="flex items-end gap-2">
            <Button type="submit">Найти</Button>
            <Button type="button" variant="outline" onClick={resetFilters}>
              Сбросить
            </Button>
          </div>
        </form>
      </CardHeader>

      <CardContent className="space-y-4">
        {error ? (
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}

        {loading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" />
            Загружаем заявки
          </div>
        ) : doctors.length === 0 ? (
          <p className="text-sm text-muted-foreground">Заявки не найдены.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[980px] text-sm">
              <thead>
                <tr className="border-b text-left text-xs uppercase tracking-wide text-muted-foreground">
                  <th className="pb-2 pr-3 font-medium">Врач</th>
                  <th className="pb-2 pr-3 font-medium">Специализация</th>
                  <th className="pb-2 pr-3 font-medium">Стаж</th>
                  <th className="pb-2 pr-3 font-medium">Лицензия</th>
                  <th className="pb-2 pr-3 font-medium">Статус</th>
                  <th className="pb-2 pr-3 font-medium">Обновлён</th>
                  <th className="pb-2 font-medium">Действия</th>
                </tr>
              </thead>
              <tbody>
                {doctors.map((doctor) => (
                  <tr key={doctor.userId} className="border-b align-top last:border-b-0">
                    <td className="py-3 pr-3">
                      <div className="font-medium">{personName(doctor)}</div>
                      <div className="text-xs text-muted-foreground">{doctor.email}</div>
                    </td>
                    <td className="py-3 pr-3">{doctor.specialization}</td>
                    <td className="py-3 pr-3">{formatExperience(doctor.yearsOfExperience)}</td>
                    <td className="py-3 pr-3 text-xs">
                      {doctor.licenseNumber ?? "—"}
                    </td>
                    <td className="py-3 pr-3">
                      <StatusBadge status={doctor.verificationStatus} />
                    </td>
                    <td className="py-3 pr-3 text-xs text-muted-foreground">
                      {formatDate(doctor.updatedAt)}
                    </td>
                    <td className="py-3">
                      <div className="flex flex-wrap gap-1">
                        {doctor.verificationStatus === "VERIFIED" ? (
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            className="text-destructive hover:text-destructive"
                            disabled={busyUserId === doctor.userId}
                            onClick={() => onReject(doctor)}
                          >
                            <ShieldX className="h-4 w-4" />
                            Отклонить
                          </Button>
                        ) : doctor.verificationStatus === "REJECTED" ? (
                          <Button
                            type="button"
                            size="sm"
                            disabled={busyUserId === doctor.userId}
                            onClick={() => onApprove(doctor)}
                          >
                            <CheckCircle2 className="h-4 w-4" />
                            Подтвердить
                          </Button>
                        ) : (
                          <>
                            <Button
                              type="button"
                              size="sm"
                              disabled={busyUserId === doctor.userId}
                              onClick={() => onApprove(doctor)}
                            >
                              <CheckCircle2 className="h-4 w-4" />
                              Подтвердить
                            </Button>
                            <Button
                              type="button"
                              size="sm"
                              variant="outline"
                              className="text-destructive hover:text-destructive"
                              disabled={busyUserId === doctor.userId}
                              onClick={() => onReject(doctor)}
                            >
                              <ShieldX className="h-4 w-4" />
                              Отклонить
                            </Button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="flex items-center justify-between gap-3">
          <span className="text-xs text-muted-foreground">
            Страница {totalPages === 0 ? 0 : page + 1} из {totalPages}
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0 || loading}
              onClick={() => setPage((value) => Math.max(0, value - 1))}
            >
              Назад
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page + 1 >= totalPages || loading}
              onClick={() => setPage((value) => value + 1)}
            >
              Вперёд
            </Button>
            <Button variant="ghost" size="sm" disabled={loading} onClick={reload}>
              <RefreshCw className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function StatusBadge({ status }: { status: string }) {
  const className =
    status === "VERIFIED"
      ? "bg-emerald-100 text-emerald-800"
      : status === "REJECTED"
        ? "bg-red-100 text-red-800"
        : "bg-amber-100 text-amber-800";
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${className}`}>
      {verificationLabels[status] ?? status}
    </span>
  );
}
