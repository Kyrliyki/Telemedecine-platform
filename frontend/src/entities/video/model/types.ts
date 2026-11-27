export type VideoCallStatus = "created" | "active" | "ended";

export interface VideoCall {
  id: string;
  appointment_id: string;
  room_name: string;
  doctor_id: string;
  patient_id: string;
  status: VideoCallStatus;
  max_participants: number;
  created_at: string;
  started_at: string | null;
  ended_at: string | null;
}

export interface JoinCallResult {
  call: VideoCall;
  token: string;
  livekit_url: string;
  expires_at: string;
}

export interface CallStartedEvent {
  type: "call.started";
  payload: {
    call_id: string;
    appointment_id: string;
    room_name: string;
    doctor_id: string;
    patient_id: string;
    status: string;
    created_at: string;
  };
}
