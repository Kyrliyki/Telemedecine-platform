import { AlertCircle, Loader2, RefreshCw, Search, Stethoscope } from "lucide-react";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { listDoctors } from "@/entities/user/api/userApi";
import {
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

const PAGE_SIZE = 12;

export function DoctorsCatalog() {
  const [specialization, setSpecialization] = useState("");
  const [appliedSpecialization, setAppliedSpecialization] = useState("");
  const [page, setPage] = useState(0);
  const [doctors, setDoctors] = useState<DoctorProfile[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setError(null);
      try {
        const result = await listDoctors({
          page,
          size: PAGE_SIZE,
          specialization: appliedSpecialization.trim() || undefined,
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
  }, [appliedSpecialization, page, reloadKey]);

  const reload = useCallback(() => setReloadKey((value) => value + 1), []);

  function applyFilters(event: FormEvent) {
    event.preventDefault();
    setPage(0);
    setAppliedSpecialization(specialization);
  }

  function resetFilters() {
    setSpecialization("");
    setAppliedSpecialization("");
    setPage(0);
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardContent className="pt-6">
          <form className="flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={applyFilters}>
            <div className="space-y-1 sm:w-80">
              <Label htmlFor="doctor-specialization" className="text-xs">
                Специализация
              </Label>
              <Input
                id="doctor-specialization"
                value={specialization}
                onChange={(event) => setSpecialization(event.target.value)}
                placeholder="Кардиолог, терапевт…"
              />
            </div>
            <div className="flex gap-2">
              <Button type="submit">
                <Search className="h-4 w-4" />
                Найти
              </Button>
              <Button type="button" variant="outline" onClick={resetFilters}>
                Сбросить
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {error ? (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : null}

      {loading ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          Загружаем врачей
        </div>
      ) : doctors.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center gap-2 py-10 text-center text-sm text-muted-foreground">
            <Stethoscope className="h-8 w-8 text-muted-foreground/60" />
            Врачи не найдены.
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {doctors.map((doctor) => (
            <Card key={doctor.userId} className="flex flex-col">
              <CardHeader className="space-y-1">
                <CardTitle className="text-lg">{personName(doctor)}</CardTitle>
                <CardDescription>{doctor.specialization}</CardDescription>
              </CardHeader>
              <CardContent className="flex flex-1 flex-col justify-between gap-4 text-sm">
                <dl className="space-y-1">
                  <div className="flex justify-between gap-2">
                    <dt className="text-muted-foreground">Стаж</dt>
                    <dd className="font-medium">
                      {formatExperience(doctor.yearsOfExperience)}
                    </dd>
                  </div>
                  <div className="flex justify-between gap-2">
                    <dt className="text-muted-foreground">Статус</dt>
                    <dd className="font-medium">
                      {verificationLabels[doctor.verificationStatus] ??
                        doctor.verificationStatus}
                    </dd>
                  </div>
                </dl>
                <Button asChild variant="outline" className="w-full">
                  <Link to={`/doctors/${doctor.userId}`}>Подробнее</Link>
                </Button>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <div className="flex items-center justify-between gap-3">
        <span className="text-xs text-muted-foreground">
          Найдено: {totalElements}. Страница {totalPages === 0 ? 0 : page + 1} из {totalPages}
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
    </div>
  );
}
