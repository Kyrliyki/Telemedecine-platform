import { AlertCircle, HeartPulse, Loader2 } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { writeSession } from "@/entities/session/model/storage";
import { OAUTH_NEXT_KEY, OAUTH_STATE_KEY } from "@/shared/config/env";
import { safeNextPath } from "@/shared/lib/redirect";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card";

const errorMessages: Record<string, string> = {
  access_denied: "Доступ через Google отменён.",
  missing_code: "Google не вернул код авторизации. Попробуйте ещё раз.",
  oauth_account_conflict:
    "Аккаунт с таким email уже существует, но email не подтверждён. Проверьте почту: перейдите по ссылке из письма, чтобы привязать Google, затем войдите снова.",
  oauth_failed: "Не удалось войти через Google. Попробуйте ещё раз.",
};

export function OAuthCallbackPage() {
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.hash.replace(/^#/, ""));
    window.history.replaceState(null, "", window.location.pathname);

    const oauthError = params.get("error");
    const state = params.get("state");
    const expectedState = window.sessionStorage.getItem(OAUTH_STATE_KEY);
    const nextPath = safeNextPath(window.sessionStorage.getItem(OAUTH_NEXT_KEY));
    window.sessionStorage.removeItem(OAUTH_STATE_KEY);
    window.sessionStorage.removeItem(OAUTH_NEXT_KEY);

    if (oauthError) {
      setError(errorMessages[oauthError] ?? "Не удалось войти через Google.");
      return;
    }

    if (!state || !expectedState || state !== expectedState) {
      setError("Сессия входа устарела. Начните вход через Google заново.");
      return;
    }

    const accessToken = params.get("access_token");
    const refreshToken = params.get("refresh_token");
    if (!accessToken || !refreshToken) {
      setError("Google не вернул токены доступа. Попробуйте ещё раз.");
      return;
    }

    writeSession({
      accessToken,
      refreshToken,
      accessExpiresAt: params.get("access_expires_at") ?? "",
      refreshExpiresAt: params.get("refresh_expires_at") ?? "",
      mustChangePassword: false,
    });
    navigate(nextPath, { replace: true });
  }, [navigate]);

  return (
    <div className="flex min-h-dvh items-center justify-center bg-[#052624] px-6 py-10">
      <Card className="w-full max-w-md border border-white/20 bg-[#1b2a28] text-slate-100 shadow-2xl shadow-black/40">
        <CardHeader className="space-y-2 p-6 pb-4">
          <div className="flex items-center gap-3 text-lg font-semibold">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary text-primary-foreground">
              <HeartPulse className="h-5 w-5" />
            </span>
            Telemed
          </div>
          <CardTitle className="text-xl">
            {error ? "Вход через Google" : "Завершаем вход…"}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4 p-6 pt-0">
          {error ? (
            <>
              <p className="flex items-start gap-3 text-sm text-red-300">
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                {error}
              </p>
              <Button asChild className="w-full">
                <Link to="/login">Вернуться ко входу</Link>
              </Button>
            </>
          ) : (
            <p className="flex items-center gap-3 text-sm text-slate-300">
              <Loader2 className="h-4 w-4 animate-spin" />
              Проверяем ответ Google и создаём сессию.
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
