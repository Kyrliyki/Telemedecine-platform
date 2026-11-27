import { AlertCircle, CalendarClock, Loader2, Stethoscope, UserRound } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { fetchMyProfile } from "@/entities/user/api/userApi";
import {
  accountStatusLabels,
  formatDate,
  roleLabels,
  verificationLabels,
} from "@/entities/user/model/display";
import type { UserProfile } from "@/entities/user/model/types";
import { ProfileForm } from "@/features/profile/ui/ProfileForm";
import { ApiError, formatApiError } from "@/shared/api/http";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/ui/card";
import { AppHeader } from "@/widgets/app-header/ui/AppHeader";

export function ProfilePage() {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setError(null);
      try {
        const profile = await fetchMyProfile();
        if (!cancelled) {
          setUser(profile);
        }
      } catch (requestError) {
        if (!cancelled) {
          if (requestError instanceof ApiError && requestError.status === 401) {
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
  }, [reloadKey]);

  const retry = useCallback(() => setReloadKey((value) => value + 1), []);

  return (
    <div className="min-h-screen bg-background">
      <AppHeader />
      <main className="mx-auto max-w-4xl space-y-6 px-6 py-10">
        <div>
          <h1 className="text-2xl font-semibold">Профиль</h1>
        </div>

        {loading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" />
            Загружаем профиль
          </div>
        ) : error ? (
          <div className="space-y-3">
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>{error}</AlertDescription>
            </Alert>
            <Button variant="outline" onClick={retry}>
              Повторить
            </Button>
          </div>
        ) : user ? (
          <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr] lg:items-stretch">
            <Card className="lg:col-start-1 lg:row-start-1">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-xl">
                  <UserRound className="h-5 w-5 text-primary" />
                  Личные данные
                </CardTitle>
                <CardDescription>{user.email}</CardDescription>
              </CardHeader>
              <CardContent>
                <ProfileForm user={user} onUpdated={setUser} />
              </CardContent>
            </Card>

            <Card className="lg:col-start-2 lg:row-start-1">
                <CardHeader>
                  <CardTitle className="text-base">Аккаунт</CardTitle>
                </CardHeader>
                <CardContent className="space-y-3 text-sm">
                  <div>
                    <div className="text-xs uppercase tracking-wide text-muted-foreground">
                      Email
                    </div>
                    <div className="font-medium">{user.email}</div>
                  </div>
                  <div>
                    <div className="text-xs uppercase tracking-wide text-muted-foreground">
                      Статус
                    </div>
                    <div className="font-medium">
                      {accountStatusLabels[user.status] ?? user.status}
                    </div>
                  </div>
                  <div>
                    <div className="text-xs uppercase tracking-wide text-muted-foreground">
                      Роли
                    </div>
                    <div className="mt-1 flex flex-wrap gap-2">
                      {user.roles.map((role) => (
                        <span
                          key={role}
                          className="rounded-full bg-secondary px-3 py-1 text-xs font-medium"
                        >
                          {roleLabels[role] ?? role}
                        </span>
                      ))}
                    </div>
                  </div>
                  <div className="flex items-start gap-2 text-xs text-muted-foreground">
                    <CalendarClock className="mt-0.5 h-4 w-4 shrink-0" />
                    <span>
                      Создан: {formatDate(user.createdAt)}
                      <br />
                      Обновлён: {formatDate(user.updatedAt)}
                    </span>
                  </div>
                </CardContent>
            </Card>

              {user.roles.includes("DOCTOR") ? (
                <Card className="lg:col-start-2 lg:row-start-2">
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2 text-base">
                      <Stethoscope className="h-4 w-4 text-primary" />
                      Профиль врача
                    </CardTitle>
                    <CardDescription>
                      {user.doctorProfile
                        ? verificationLabels[user.doctorProfile.verificationStatus] ??
                          user.doctorProfile.verificationStatus
                        : "Профиль ещё не заполнен"}
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-3 text-sm">
                    {user.doctorProfile ? (
                      <dl className="space-y-2">
                        <div>
                          <dt className="text-xs uppercase tracking-wide text-muted-foreground">
                            Специализация
                          </dt>
                          <dd className="font-medium">
                            {user.doctorProfile.specialization}
                          </dd>
                        </div>
                        <div>
                          <dt className="text-xs uppercase tracking-wide text-muted-foreground">
                            Стаж
                          </dt>
                          <dd className="font-medium">
                            {user.doctorProfile.yearsOfExperience} лет
                          </dd>
                        </div>
                        {user.doctorProfile.rejectionReason ? (
                          <div className="rounded-md bg-destructive/10 p-2 text-xs text-destructive">
                            Причина отклонения: {user.doctorProfile.rejectionReason}
                          </div>
                        ) : null}
                      </dl>
                    ) : null}
                    <Button asChild variant="outline" className="w-full">
                      <Link to="/doctor/profile">Открыть кабинет врача</Link>
                    </Button>
                  </CardContent>
                </Card>
              ) : null}
          </div>
        ) : null}
      </main>
    </div>
  );
}
