import { authRequest } from "@/entities/session/api/session";

export interface AuthAuditEntry {
  id: string;
  actorId: string | null;
  targetUserId: string | null;
  action: string;
  outcome: string;
  ipAddress: string | null;
  userAgent: string | null;
  requestId: string | null;
  correlationId: string | null;
  details: string | null;
  createdAt: string;
}

export interface AuthAuditPage {
  content: AuthAuditEntry[];
  totalElements: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface AuthAuditFilters {
  action?: string;
  targetUserId?: string;
  page?: number;
  size?: number;
}

export function searchAuthAudit(filters: AuthAuditFilters = {}): Promise<AuthAuditPage> {
  const search = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return authRequest<AuthAuditPage>(`/api/v1/admin/auth/audit${query ? `?${query}` : ""}`);
}
