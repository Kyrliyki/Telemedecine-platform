import { authRequest } from "@/entities/session/api/session";

export interface AdminUser {
  userId: string;
  email: string;
  emailVerified: boolean;
  status: string;
  roles: string[];
  createdAt: string;
  updatedAt: string;
}

export interface AdminUserPage {
  content: AdminUser[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AdminUserFilters {
  page?: number;
  size?: number;
  email?: string;
  status?: string;
  role?: string;
}

export interface InvitationResult {
  userId: string;
  email: string;
  expiresAt: string;
}

function queryString(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return query ? `?${query}` : "";
}

export function listAdminUsers(filters: AdminUserFilters = {}): Promise<AdminUserPage> {
  return authRequest<AdminUserPage>(`/api/v1/admin/users${queryString({ ...filters })}`);
}

export function inviteUser(email: string, roles: string[]): Promise<InvitationResult> {
  return authRequest<InvitationResult>("/api/v1/admin/users/invite", {
    method: "POST",
    body: { email, roles },
  });
}

export function assignRole(userId: string, role: string): Promise<void> {
  return authRequest<void>(`/api/v1/admin/users/${encodeURIComponent(userId)}/roles`, {
    method: "POST",
    body: { role },
  });
}

export function revokeRole(userId: string, role: string): Promise<void> {
  return authRequest<void>(
    `/api/v1/admin/users/${encodeURIComponent(userId)}/roles/${encodeURIComponent(role)}`,
    { method: "DELETE" },
  );
}

export function blockUser(userId: string, reason?: string): Promise<void> {
  return authRequest<void>(`/api/v1/admin/users/${encodeURIComponent(userId)}/block`, {
    method: "POST",
    body: { reason: reason ?? null },
  });
}

export function unblockUser(userId: string): Promise<void> {
  return authRequest<void>(`/api/v1/admin/users/${encodeURIComponent(userId)}/unblock`, {
    method: "POST",
  });
}

