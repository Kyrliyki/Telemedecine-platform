import {
  AlertCircle,
  BadgeCheck,
  CalendarDays,
  Clock3,
  Loader2,
  ShieldX,
  Stethoscope,
} from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { fetchMyDoctorProfile } from "@/entities/user/api/userApi";
import { verificationLabels } from "@/entities/user/model/display";
import type { DoctorProfile } from "@/entities/user/model/types";
import { DoctorProfileForm } from "@/features/doctor-profile/ui/DoctorProfileForm";
import { DoctorScheduleForm } from "@/features/doctor-schedule/ui/DoctorScheduleForm";
import { ApiError, formatApiError } from "@/shared/api/http";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/ui/card";
import { AppHeader } from "@/widgets/app-header/ui/AppHeader";

export function DoctorProfilePage() {
  const [profile, setProfile] = useState<DoctorProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setError(null);
      try {
        const loaded = await fetchMyDoctorProfile();
        if (!cancelled) {
          setProfile(loaded);
        }
      } catch (requestError) {
        if (!cancelled) {
          if (
            requestError instanceof ApiError &&
            requestError.code === "DOCTOR_PROFILE_NOT_FOUND"
          ) {
            setProfile(null);
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

    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="min-h-screen bg-background">
      <AppHeader />
      <main className="mx-auto max-w-4xl space-y-6 px-6 py-10">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-semibold">
            <Stethoscope className="h-6 w-6 text-primary" />
            Кабинет врача
          </h1>
          <p className="text-sm text-muted-foreground">
            Профиль виден пациентам только после проверки администратором.
          </p>
        </div>

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
        ) : (
          <>
            <VerificationBanner profile={profile} />

            <Card>
              <CardHeader>
                <CardTitle className="text-xl">
                  {profile ? "Профиль врача" : "Заполните профиль врача"}
                </CardTitle>
                <CardDescription>
                  {profile
                    ? `Текущий статус: ${
                        verificationLabels[profile.verificationStatus] ??
                        profile.verificationStatus
                      }`
                    : "После сохранения профиль уйдёт на проверку администратору."}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <DoctorProfileForm profile={profile} onSaved={setProfile} />
              </CardContent>
            </Card>

            {profile ? (
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2 text-xl">
                    <CalendarDays className="h-5 w-5 text-primary" />
                    График работы
                  </CardTitle>
                  <CardDescription>
                    Отметьте рабочие дни, время приёма и период, на который действует график.
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <DoctorScheduleForm canEdit={profile.verificationStatus === "VERIFIED"} />
                </CardContent>
              </Card>
            ) : null}

            {profile?.verificationStatus === "VERIFIED" ? (
              <Button asChild variant="outline">
                <Link to={`/doctors/${profile.userId}`}>Открыть публичный профиль</Link>
              </Button>
            ) : null}
          </>
        )}
      </main>
    </div>
  );
}

function VerificationBanner({ profile }: { profile: DoctorProfile | null }) {
  if (!profile) {
    return (
      <Alert>
        <Clock3 className="h-4 w-4" />
        <AlertDescription>
          Профиль ещё не создан. Заполните специализацию и сохраните — заявка появится у
          администратора.
        </AlertDescription>
      </Alert>
    );
  }

  if (profile.verificationStatus === "VERIFIED") {
    return (
      <Alert className="border-emerald-200 bg-emerald-50 text-emerald-900">
        <BadgeCheck className="h-4 w-4" />
        <AlertDescription>Профиль проверен администратором.</AlertDescription>
      </Alert>
    );
  }

  if (profile.verificationStatus === "REJECTED") {
    return (
      <Alert variant="destructive">
        <ShieldX className="h-4 w-4" />
        <AlertDescription>
          Профиль отклонён.
          {profile.rejectionReason ? ` Причина: ${profile.rejectionReason}` : ""}
          {" "}Исправьте данные и сохраните — заявка уйдёт на повторную проверку.
        </AlertDescription>
      </Alert>
    );
  }

  return (
    <Alert className="border-amber-200 bg-amber-50 text-amber-900">
      <Clock3 className="h-4 w-4" />
      <AlertDescription>
        Профиль на проверке. До одобрения он не отображается в каталоге и недоступен
        пациентам.
      </AlertDescription>
    </Alert>
  );
}
