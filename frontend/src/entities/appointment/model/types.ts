export type AppointmentStatus =
  | "requested"
  | "confirmed"
  | "cancelled"
  | "completed"
  | "no_show";

export interface Appointment {
  id: string;
  patient_id: string;
  doctor_id: string;
  starts_at: string;
  ends_at: string;
  status: AppointmentStatus;
  reason: string;
  cancellation_reason: string | null;
  created_at: string;
  updated_at: string;
  version: number;
}

export interface AppointmentListResponse {
  items: Appointment[];
  total: number;
  limit: number;
  offset: number;
}

export interface AppointmentListQuery {
  status?: AppointmentStatus;
  patient_id?: string;
  doctor_id?: string;
  view?: "patient" | "doctor";
  starts_after?: string;
  starts_before?: string;
  limit?: number;
  offset?: number;
}

export interface CreateAppointmentPayload {
  doctor_id: string;
  starts_at: string;
  ends_at: string;
  reason: string;
  patient_id?: string;
}

 
export const ACTIVE_APPOINTMENT_STATUSES: AppointmentStatus[] = ["requested", "confirmed"];
