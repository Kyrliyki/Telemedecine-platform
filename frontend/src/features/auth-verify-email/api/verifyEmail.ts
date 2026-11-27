import { apiRequest } from "@/shared/api/http";

export function verifyEmail(token: string): Promise<void> {
  return apiRequest<void>("/api/v1/auth/verify-email", {
    method: "POST",
    body: { token },
  });
}
