import { authRequest } from "@/entities/session/api/session";
import type { JoinCallResult, VideoCall } from "@/entities/video/model/types";

export interface CreateCallPayload {
  appointment_id: string;
  patient_id: string;
  doctor_id?: string;
}

export function createCall(payload: CreateCallPayload): Promise<VideoCall> {
  return authRequest<VideoCall>("/api/v1/video", {
    method: "POST",
    body: payload,
  });
}

export function joinCall(callId: string, displayName?: string): Promise<JoinCallResult> {
  return authRequest<JoinCallResult>(
    `/api/v1/video/${encodeURIComponent(callId)}/token`,
    { method: "POST", body: { display_name: displayName } },
  );
}

export function endCall(callId: string): Promise<VideoCall> {
  return authRequest<VideoCall>(`/api/v1/video/${encodeURIComponent(callId)}/end`, {
    method: "POST",
  });
}
