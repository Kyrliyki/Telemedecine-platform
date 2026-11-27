import { Navigate } from "react-router-dom";

import { hasActiveSession } from "@/entities/session/model/storage";
import { useSession } from "@/entities/session/model/useSession";
import { RegisterForm } from "@/features/auth-register/ui/RegisterForm";
import { AuthLayout } from "@/widgets/auth-layout/ui/AuthLayout";

export function RegisterPage() {
  const session = useSession();

  if (hasActiveSession(session)) {
    return <Navigate to="/" replace />;
  }

  return (
    <AuthLayout>
      <RegisterForm />
    </AuthLayout>
  );
}
