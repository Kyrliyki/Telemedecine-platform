import { apiRequest } from "@/shared/api/http";

export interface ConfirmOAuthLinkPayload {
  token: string;
  provider: string;
}

export function confirmOAuthLink(payload: ConfirmOAuthLinkPayload): Promise<void> {
  return apiRequest<void>("/api/v1/auth/oauth/confirm", {
    method: "POST",
    body: payload,
  });
}
