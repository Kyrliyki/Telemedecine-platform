import { zodResolver } from "@hookform/resolvers/zod";
import { AlertCircle, CheckCircle2, Loader2 } from "lucide-react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { z } from "zod";

import { login } from "@/features/auth-login/api/login";
import { GoogleLoginButton } from "@/features/auth-google/ui/GoogleLoginButton";
import { writeSession } from "@/entities/session/model/storage";
import { formatApiError } from "@/shared/api/http";
import { safeNextPath } from "@/shared/lib/redirect";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/shared/ui/card";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";
import { PasswordInput } from "@/shared/ui/password-input";

const loginSchema = z.object({
  email: z.string().trim().min(1, "Введите email").email("Некорректный email"),
  password: z.string().min(1, "Введите пароль"),
});

type LoginValues = z.infer<typeof loginSchema>;

const fieldClassName =
  "border-white/15 bg-white/10 text-slate-100 placeholder:text-slate-400";

export function LoginForm() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const nextPath = safeNextPath(searchParams.get("next"));
  const justVerified = searchParams.get("verified") === "1";
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    try {
      const session = await login(values);
      writeSession(session);
      navigate(nextPath, { replace: true });
    } catch (error) {
      setError("root", { message: formatApiError(error) });
    }
  });

  return (
    <Card className="border border-white/20 bg-[#1b2a28] text-slate-100 shadow-2xl shadow-black/40">
      <CardHeader className="space-y-1 p-6 pb-4">
        <CardTitle>Авторизация</CardTitle>
      </CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={onSubmit} noValidate>
          {justVerified ? (
            <Alert className="border-emerald-500/40 bg-emerald-500/10 text-emerald-100 [&>svg]:text-emerald-300">
              <CheckCircle2 className="h-4 w-4" />
              <AlertDescription>Email подтверждён. Теперь войдите в аккаунт.</AlertDescription>
            </Alert>
          ) : null}

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
            <Label htmlFor="login-email">Email</Label>
            <Input
              id="login-email"
              type="email"
              autoComplete="email"
              placeholder="doctor@example.com"
              className={fieldClassName}
              {...register("email")}
            />
            {errors.email?.message ? (
              <p className="text-sm text-red-400">{errors.email.message}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="login-password">Пароль</Label>
            <PasswordInput
              id="login-password"
              autoComplete="current-password"
              placeholder="Введите пароль"
              className={fieldClassName}
              {...register("password")}
            />
            {errors.password?.message ? (
              <p className="text-sm text-red-400">{errors.password.message}</p>
            ) : null}
          </div>

          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            Войти
          </Button>
        </form>

        <p className="mt-5 text-center text-sm text-muted-foreground">
          Нет аккаунта?{" "}
          <Link className="font-medium text-emerald-300 hover:underline" to="/register">
            Зарегистрироваться
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}
