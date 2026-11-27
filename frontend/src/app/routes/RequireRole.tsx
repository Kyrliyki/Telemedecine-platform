import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";

import { hasRole } from "@/entities/session/model/jwt";
import { useSession } from "@/entities/session/model/useSession";
import { RequireAuth } from "@/app/routes/RequireAuth";

interface RequireRoleProps {
  role: string;
  children: ReactNode;
}

export function RequireRole({ role, children }: RequireRoleProps) {
  const session = useSession();

  return (
    <RequireAuth>
      {session && !hasRole(session.accessToken, role) ? <Navigate to="/" replace /> : children}
    </RequireAuth>
  );
}
