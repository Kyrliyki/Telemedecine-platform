import { apiRequest } from "@/shared/api/http";

export interface AcceptInvitationPayload {
  token: string;
  password: string;
}

export interface AcceptInvitationResult {
  userId: string;
  email: string;
  roles: string[];
  status: string;
  invitationRequired: boolean;
}

export function acceptInvitation(
  payload: AcceptInvitationPayload,
): Promise<AcceptInvitationResult> {
  return apiRequest<AcceptInvitationResult>("/api/v1/auth/accept-invitation", {
    method: "POST",
    body: payload,
  });
}
