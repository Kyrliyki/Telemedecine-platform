import { apiRequest } from "@/shared/api/http";

export interface RegisterPayload {
  email: string;
  password: string;
}

export interface RegistrationResult {
  userId: string;
  email: string;
  roles: string[];
  status: string;
  invitationRequired: boolean;
}

export function register(payload: RegisterPayload): Promise<RegistrationResult> {
  return apiRequest<RegistrationResult>("/api/v1/auth/register", {
    method: "POST",
    body: payload,
  });
}
