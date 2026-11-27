import { AlertCircle, ArrowLeft, BadgeCheck, Loader2, Stethoscope } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";

import { fetchDoctorProfile } from "@/entities/user/api/userApi";
import {
  formatDate,
  formatExperience,
  personName,
} from "@/entities/user/model/display";
import type { DoctorProfile } from "@/entities/user/model/types";
import { ApiError, formatApiError } from "@/shared/api/http";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/ui/card";
import { AppHeader } from "@/widgets/app-header/ui/AppHeader";

export function DoctorDetailPage() {
  const { userId = "" } = useParams();
  const [doctor, setDoctor] = useState<DoctorProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setError(null);
      try {
        const profile = await fetchDoctorProfile(userId);
        if (!cancelled) {
          setDoctor(profile);
        }
      } catch (requestError) {
        if (!cancelled) {
          if (
            requestError instanceof ApiError &&
            (requestError.code === "DOCTOR_PROFILE_NOT_FOUND" || requestError.status === 404)
          ) {
            setError("Врач не найден или профиль ещё не прошёл проверку.");
            return;
          }
          setError(formatApiError(requestError));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    if (userId) {
      void load();
    } else {
      setLoading(false);
      setError("Не указан идентификатор врача.");
    }
    return () => {
      cancelled = true;
    };
  }, [userId]);

  return (
    <div className="min-h-screen bg-background">
      <AppHeader />
      <main className="mx-auto max-w-4xl space-y-6 px-6 py-10">
        <Button asChild variant="ghost" size="sm" className="-ml-3">
          <Link to="/doctors">
            <ArrowLeft className="h-4 w-4" />
            Ко всем врачам
          </Link>
        </Button>

        {loading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" />
            Загружаем профиль врача
          </div>
        ) : error ? (
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : doctor ? (
          <Card>
            <CardHeader>
              <CardTitle className="flex flex-wrap items-center gap-2 text-2xl">
                <Stethoscope className="h-5 w-5 text-primary" />
                {personName(doctor)}
                <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-3 py-1 text-xs font-medium text-emerald-800">
                  <BadgeCheck className="h-3 w-3" />
                  Проверен
                </span>
              </CardTitle>
              <CardDescription>{doctor.specialization}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              {doctor.bio ? (
                <p className="whitespace-pre-line text-sm leading-relaxed text-foreground">
                  {doctor.bio}
                </p>
              ) : (
                <p className="text-sm text-muted-foreground">Описание пока не заполнено.</p>
              )}

              <dl className="grid gap-4 sm:grid-cols-2">
                <div>
                  <dt className="text-xs uppercase tracking-wide text-muted-foreground">Стаж</dt>
                  <dd className="text-sm font-medium">
                    {formatExperience(doctor.yearsOfExperience)}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs uppercase tracking-wide text-muted-foreground">
                    Лицензия
                  </dt>
                  <dd className="text-sm font-medium">{doctor.licenseNumber ?? "—"}</dd>
                </div>
                <div>
                  <dt className="text-xs uppercase tracking-wide text-muted-foreground">Email</dt>
                  <dd className="text-sm font-medium">{doctor.email}</dd>
                </div>
                <div>
                  <dt className="text-xs uppercase tracking-wide text-muted-foreground">
                    Проверен
                  </dt>
                  <dd className="text-sm font-medium">{formatDate(doctor.verifiedAt)}</dd>
                </div>
              </dl>
            </CardContent>
          </Card>
        ) : null}
      </main>
    </div>
  );
}
