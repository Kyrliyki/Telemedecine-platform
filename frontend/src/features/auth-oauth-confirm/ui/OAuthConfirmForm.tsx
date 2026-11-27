import { AlertCircle, CheckCircle2, Loader2, Link2 } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

import { confirmOAuthLink } from "@/features/auth-oauth-confirm/api/confirmOAuthLink";
import { ApiError, formatApiError } from "@/shared/api/http";
import { Button } from "@/shared/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/shared/ui/card";

const providerNames: Record<string, string> = {
  GOOGLE: "Google",
};

const linkErrors: Record<string, string> = {
  INVALID_OAUTH_CONFIRMATION_TOKEN:
    "Ссылка недействительна или истекла. Запросите новое письмо, выполнив вход через Google ещё раз.",
  OAUTH_ALREADY_LINKED: "Этот аккаунт уже привязан к выбранному провайдеру.",
};

interface OAuthConfirmFormProps {
  token: string;
  provider: string;
}

export function OAuthConfirmForm({ token, provider }: OAuthConfirmFormProps) {
  const [status, setStatus] = useState<"idle" | "loading" | "done">("idle");
  const [error, setError] = useState<string | null>(null);
  const providerName = providerNames[provider.toUpperCase()] ?? provider;

  async function onConfirm() {
    setStatus("loading");
    setError(null);
    try {
      await confirmOAuthLink({ token, provider });
      setStatus("done");
    } catch (caught) {
      setStatus("idle");
      if (caught instanceof ApiError && caught.code && linkErrors[caught.code]) {
        setError(linkErrors[caught.code]);
        return;
      }
      setError(formatApiError(caught));
    }
  }

  if (status === "done") {
    return (
      <Card className="border border-white/20 bg-[#1b2a28] text-slate-100 shadow-2xl shadow-black/40">
        <CardHeader className="space-y-1 p-6 pb-4">
          <div className="mb-2 flex h-11 w-11 items-center justify-center rounded-full bg-emerald-500/10 text-emerald-300">
            <CheckCircle2 className="h-6 w-6" />
          </div>
          <CardTitle>{providerName} привязан</CardTitle>
          <CardDescription className="text-slate-400">
            Теперь в этот аккаунт можно входить через {providerName}.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-slate-400">
            Пароль от аккаунта остался прежним — оба способа входа работают.
          </p>
          <Button asChild className="w-full">
            <Link to="/login">Перейти ко входу</Link>
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="border border-white/20 bg-[#1b2a28] text-slate-100 shadow-2xl shadow-black/40">
      <CardHeader className="space-y-1 p-6 pb-4">
        <div className="mb-2 flex h-11 w-11 items-center justify-center rounded-full bg-emerald-500/10 text-emerald-300">
          <Link2 className="h-6 w-6" />
        </div>
        <CardTitle>Привязать {providerName}</CardTitle>
        <CardDescription className="text-slate-400">
          Аккаунт с этим email уже существует. Подтвердите привязку, чтобы входить
          через {providerName}.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {error ? (
          <p className="flex items-start gap-3 text-sm text-red-300">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
            {error}
          </p>
        ) : (
          <p className="text-sm text-slate-400">
            Пароль и роли аккаунта не изменятся. Привязка одноразовая: после
            подтверждения ссылка перестанет работать.
          </p>
        )}

        <Button
          type="button"
          className="w-full"
          disabled={status === "loading"}
          onClick={onConfirm}
        >
          {status === "loading" ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
          Подтвердить привязку
        </Button>

        <p className="text-center text-sm text-slate-400">
          <Link className="font-medium text-emerald-300 hover:underline" to="/login">
            Вернуться ко входу
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}
