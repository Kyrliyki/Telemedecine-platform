import { zodResolver } from "@hookform/resolvers/zod";
import { AlertCircle, CheckCircle2, Loader2 } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { z } from "zod";

import {
  register as registerAccount,
  type RegistrationResult,
} from "@/features/auth-register/api/register";
import { GoogleLoginButton } from "@/features/auth-google/ui/GoogleLoginButton";
import { formatApiError } from "@/shared/api/http";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/shared/ui/card";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";
import { PasswordInput } from "@/shared/ui/password-input";

const registerSchema = z
  .object({
    email: z.string().trim().min(1, "Введите email").email("Некорректный email"),
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

type RegisterValues = z.infer<typeof registerSchema>;

const fieldClassName =
  "border-white/15 bg-white/10 text-slate-100 placeholder:text-slate-400";

export function RegisterForm() {
  const [result, setResult] = useState<RegistrationResult | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { email: "", password: "", confirmPassword: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    try {
      const created = await registerAccount({
        email: values.email,
        password: values.password,
      });
      setResult(created);
    } catch (error) {
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
          <CardTitle>Аккаунт создан</CardTitle>
          <CardDescription className="text-slate-400">{result.email}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-slate-400">
            {result.invitationRequired
              ? "Ожидайте приглашения: доступ откроется после подтверждения администратором."
              : "Мы отправили письмо для подтверждения email. Перейдите по ссылке из письма, затем войдите в аккаунт."}
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
        <CardTitle>Регистрация</CardTitle>
      </CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={onSubmit} noValidate>
          {errors.root?.message ? (
            <Alert variant="destructive" className="border-red-500/40 bg-red-500/10 text-red-200 [&>svg]:text-red-300">
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>{errors.root.message}</AlertDescription>
            </Alert>
          ) : null}

          <GoogleLoginButton />

          <div className="relative">
            <div className="absolute inset-0 flex items-center">
              <span className="w-full border-t border-white/15" />
            </div>
            <div className="relative flex justify-center text-xs uppercase">
              <span className="bg-[#1b2a28] px-2 text-slate-400">или</span>
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="register-email">Email</Label>
            <Input
              id="register-email"
              type="email"
              autoComplete="email"
              placeholder="you@example.com"
              className={fieldClassName}
              {...register("email")}
            />
            {errors.email?.message ? (
              <p className="text-sm text-red-400">{errors.email.message}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="register-password">Пароль</Label>
            <PasswordInput
              id="register-password"
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
            <Label htmlFor="register-confirm">Повторите пароль</Label>
            <PasswordInput
              id="register-confirm"
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
            Создать аккаунт
          </Button>
        </form>

        <p className="mt-5 text-center text-sm text-slate-400">
          Уже есть аккаунт?{" "}
          <Link className="font-medium text-emerald-300 hover:underline" to="/login">
            Войти
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}
