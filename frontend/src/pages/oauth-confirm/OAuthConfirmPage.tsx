import { AlertCircle } from "lucide-react";
import { Link, useSearchParams } from "react-router-dom";

import { OAuthConfirmForm } from "@/features/auth-oauth-confirm/ui/OAuthConfirmForm";
import { Button } from "@/shared/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/shared/ui/card";
import { AuthLayout } from "@/widgets/auth-layout/ui/AuthLayout";

export function OAuthConfirmPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token")?.trim() ?? "";
  const provider = searchParams.get("provider")?.trim() ?? "GOOGLE";

  return (
    <AuthLayout>
      {token ? (
        <OAuthConfirmForm token={token} provider={provider} />
      ) : (
        <Card className="border border-white/20 bg-[#1b2a28] text-slate-100 shadow-2xl shadow-black/40">
          <CardHeader className="space-y-1 p-6 pb-4">
            <div className="mb-2 flex h-11 w-11 items-center justify-center rounded-full bg-red-500/10 text-red-300">
              <AlertCircle className="h-6 w-6" />
            </div>
            <CardTitle>Ссылка недействительна</CardTitle>
            <CardDescription className="text-slate-400">
              В ссылке нет токена подтверждения.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-slate-400">
              Откройте ссылку из письма целиком. Если письмо устарело, повторите
              вход через Google — придёт новое.
            </p>
            <Button asChild className="w-full">
              <Link to="/login">Перейти ко входу</Link>
            </Button>
          </CardContent>
        </Card>
      )}
    </AuthLayout>
  );
}
