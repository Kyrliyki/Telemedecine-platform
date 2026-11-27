import { zodResolver } from "@hookform/resolvers/zod";
import { AlertCircle, CheckCircle2, Loader2 } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { z } from "zod";

import {
  acceptInvitation,
  type AcceptInvitationResult,
} from "@/features/auth-accept-invitation/api/acceptInvitation";
import { ApiError, formatApiError } from "@/shared/api/http";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/shared/ui/card";
import { Label } from "@/shared/ui/label";
import { PasswordInput } from "@/shared/ui/password-input";

const acceptSchema = z
  .object({
    password: z
      .string()
      .min(12, "Пароль должен содержать минимум 12 символов")
      .max(128, "Пароль слишком длинный"),
    confirmPassword: z.string().min(1, "Повторите пароль"),
  })
  .refine((values) => values.password === values.confirmPassword, {
    message: "Пароли не совпадают",
    path: ["confirmPassword"],
  });

type AcceptValues = z.infer<typeof acceptSchema>;

const fieldClassName =
  "border-white/15 bg-white/10 text-slate-100 placeholder:text-slate-400";

const invitationErrors: Record<string, string> = {
  INVITATION_EXPIRED: "Срок действия приглашения истёк. Попросите администратора отправить новое.",
  INVITATION_ALREADY_USED: "Приглашение уже использовано. Войдите в аккаунт.",
  INVALID_INVITATION: "Ссылка приглашения недействительна. Проверьте, что она скопирована полностью.",
};

interface AcceptInvitationFormProps {
  token: string;
}

export function AcceptInvitationForm({ token }: AcceptInvitationFormProps) {
  const [result, setResult] = useState<AcceptInvitationResult | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<AcceptValues>({
    resolver: zodResolver(acceptSchema),
    defaultValues: { password: "", confirmPassword: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    try {
      const accepted = await acceptInvitation({ token, password: values.password });
      setResult(accepted);
    } catch (error) {
      if (error instanceof ApiError && error.code && invitationErrors[error.code]) {
        setError("root", { message: invitationErrors[error.code] });
        return;
      }
      setError("root", { message: formatApiError(error) });
    }
  });

  if (result) {
    return (
      <Card className="border border-white/20 bg-[#1b2a28] text-slate-100 shadow-2xl shadow-black/40">
        <CardHeader className="space-y-1 p-6 pb-4">
          <div className="mb-2 flex h-11 w-11 items-center justify-center rounded-full bg-emerald-500/10 text-emerald-300">
            <CheckCircle2 className="h-6 w-6" />
          </div>
          <CardTitle>Аккаунт активирован</CardTitle>
          <CardDescription className="text-slate-400">{result.email}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-slate-400">
            Пароль установлен. Теперь войдите в аккаунт с этим email и паролем.
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
        <CardTitle>Приглашение</CardTitle>
        <CardDescription className="text-slate-400">
          Задайте пароль, чтобы активировать аккаунт.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={onSubmit} noValidate>
          {errors.root?.message ? (
            <Alert
              variant="destructive"
              className="border-red-500/40 bg-red-500/10 text-red-200 [&>svg]:text-red-300"
            >
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>{errors.root.message}</AlertDescription>
            </Alert>
          ) : null}

          <div className="space-y-2">
            <Label htmlFor="accept-password">Пароль</Label>
            <PasswordInput
              id="accept-password"
              autoComplete="new-password"
              placeholder="Не менее 12 символов"
              className={fieldClassName}
              {...register("password")}
            />
            {errors.password?.message ? (
              <p className="text-sm text-red-400">{errors.password.message}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="accept-confirm">Повторите пароль</Label>
            <PasswordInput
              id="accept-confirm"
              autoComplete="new-password"
              placeholder="Повторите пароль"
              className={fieldClassName}
              {...register("confirmPassword")}
            />
            {errors.confirmPassword?.message ? (
              <p className="text-sm text-red-400">{errors.confirmPassword.message}</p>
            ) : null}
          </div>

          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            Активировать аккаунт
          </Button>
        </form>

        <p className="mt-5 text-center text-sm text-slate-400">
          Уже активировали?{" "}
          <Link className="font-medium text-emerald-300 hover:underline" to="/login">
            Войти
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}
