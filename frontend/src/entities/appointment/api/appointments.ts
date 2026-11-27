import { authRequest } from "@/entities/session/api/session";
import type {
  Appointment,
  AppointmentListQuery,
  AppointmentListResponse,
  CreateAppointmentPayload,
} from "@/entities/appointment/model/types";

function queryString(params: AppointmentListQuery): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return query ? `?${query}` : "";
}

export function listAppointments(
  query: AppointmentListQuery = {},
): Promise<AppointmentListResponse> {
  return authRequest<AppointmentListResponse>(`/api/v1/appointments${queryString(query)}`);
}

export function createAppointment(payload: CreateAppointmentPayload): Promise<Appointment> {
  return authRequest<Appointment>("/api/v1/appointments", {
    method: "POST",
    body: payload,
  });
}

export function confirmAppointment(appointmentId: string): Promise<Appointment> {
  return authRequest<Appointment>(
    `/api/v1/appointments/${encodeURIComponent(appointmentId)}/confirm`,
    { method: "POST" },
  );
}

export function completeAppointment(appointmentId: string): Promise<Appointment> {
  return authRequest<Appointment>(
    `/api/v1/appointments/${encodeURIComponent(appointmentId)}/complete`,
    { method: "POST" },
  );
}

export function cancelAppointment(
  appointmentId: string,
  reason: string,
): Promise<Appointment> {
  return authRequest<Appointment>(
    `/api/v1/appointments/${encodeURIComponent(appointmentId)}/cancel`,
    { method: "POST", body: { reason } },
  );
}
