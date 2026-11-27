import { Navigate, useSearchParams } from "react-router-dom";

import { hasActiveSession } from "@/entities/session/model/storage";
import { useSession } from "@/entities/session/model/useSession";
import { LoginForm } from "@/features/auth-login/ui/LoginForm";
import { safeNextPath } from "@/shared/lib/redirect";
import { AuthLayout } from "@/widgets/auth-layout/ui/AuthLayout";

export function LoginPage() {
  const session = useSession();
  const [searchParams] = useSearchParams();

  if (hasActiveSession(session)) {
    return <Navigate to={safeNextPath(searchParams.get("next"))} replace />;
  }

  return (
    <AuthLayout>
      <LoginForm />
    </AuthLayout>
  );
}
