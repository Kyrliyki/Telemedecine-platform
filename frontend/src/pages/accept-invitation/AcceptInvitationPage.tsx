import { AlertCircle } from "lucide-react";
import { Link, useSearchParams } from "react-router-dom";

import { AcceptInvitationForm } from "@/features/auth-accept-invitation/ui/AcceptInvitationForm";
import { Button } from "@/shared/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/shared/ui/card";
import { AuthLayout } from "@/widgets/auth-layout/ui/AuthLayout";

export function AcceptInvitationPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token")?.trim() ?? "";

  return (
    <AuthLayout>
      {token ? (
        <AcceptInvitationForm token={token} />
      ) : (
        <Card className="border border-white/20 bg-[#1b2a28] text-slate-100 shadow-2xl shadow-black/40">
          <CardHeader className="space-y-1 p-6 pb-4">
            <div className="mb-2 flex h-11 w-11 items-center justify-center rounded-full bg-red-500/10 text-red-300">
              <AlertCircle className="h-6 w-6" />
            </div>
            <CardTitle>Ссылка недействительна</CardTitle>
            <CardDescription className="text-slate-400">
              В ссылке нет токена приглашения.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-slate-400">
              Откройте ссылку из письма целиком. Если письмо устарело, попросите
              администратора отправить приглашение повторно.
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
