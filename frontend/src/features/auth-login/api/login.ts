import { apiRequest } from "@/shared/api/http";
import { toSession } from "@/entities/session/api/session";
import type { Session, TokenResponse } from "@/entities/session/model/types";

export interface LoginPayload {
  email: string;
  password: string;
}

export async function login(payload: LoginPayload): Promise<Session> {
  const response = await apiRequest<TokenResponse>("/api/v1/auth/login", {
    method: "POST",
    body: payload,
  });
  return toSession(response);
}
