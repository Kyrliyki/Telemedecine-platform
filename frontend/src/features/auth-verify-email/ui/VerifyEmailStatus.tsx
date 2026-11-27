import { AlertCircle, CheckCircle2, Loader2, MailCheck } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

import { verifyEmail } from "@/features/auth-verify-email/api/verifyEmail";
import { ApiError, formatApiError } from "@/shared/api/http";
import { Button } from "@/shared/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/shared/ui/card";

const verificationErrors: Record<string, string> = {
  INVALID_VERIFICATION_TOKEN:
    "Ссылка недействительна или истекла. Зарегистрируйтесь повторно или запросите новое письмо.",
  USER_BLOCKED: "Аккаунт заблокирован. Обратитесь к администратору.",
};

interface VerifyEmailStatusProps {
  token: string;
}

export function VerifyEmailStatus({ token }: VerifyEmailStatusProps) {
  const [status, setStatus] = useState<"idle" | "loading" | "done">("idle");
  const [error, setError] = useState<string | null>(null);

  async function onVerify() {
    setStatus("loading");
    setError(null);
    try {
      await verifyEmail(token);
      setStatus("done");
    } catch (caught) {
      setStatus("idle");
      if (caught instanceof ApiError && caught.code && verificationErrors[caught.code]) {
        setError(verificationErrors[caught.code]);
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
          <CardTitle>Email подтверждён</CardTitle>
          <CardDescription className="text-slate-400">
            Аккаунт активирован. Теперь можно войти.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <Button asChild className="w-full">
            <Link to="/login?verified=1">Перейти ко входу</Link>
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="border border-white/20 bg-[#1b2a28] text-slate-100 shadow-2xl shadow-black/40">
      <CardHeader className="space-y-1 p-6 pb-4">
        <div className="mb-2 flex h-11 w-11 items-center justify-center rounded-full bg-emerald-500/10 text-emerald-300">
          <MailCheck className="h-6 w-6" />
        </div>
        <CardTitle>Подтверждение email</CardTitle>
        <CardDescription className="text-slate-400">
          Осталось подтвердить адрес — и аккаунт станет активным.
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
            Мы не подтверждаем email автоматически при открытии ссылки, чтобы
            почтовые сканеры не расходовали одноразовый токен.
          </p>
        )}

        <Button
          type="button"
          className="w-full"
          disabled={status === "loading"}
          onClick={onVerify}
        >
          {status === "loading" ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
          Подтвердить email
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
