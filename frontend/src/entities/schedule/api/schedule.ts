import { authRequest } from "@/entities/session/api/session";
import type {
  ScheduleSlot,
  UpdateWorkingHoursPayload,
  WorkingHoursRule,
} from "@/entities/schedule/model/types";

export function getDoctorAvailability(
  doctorId: string,
  from: string,
  to: string,
  limit = 500,
): Promise<ScheduleSlot[]> {
  const params = new URLSearchParams({
    from,
    to,
    limit: String(limit),
  });
  return authRequest<ScheduleSlot[]>(
    `/api/v1/schedule/doctors/${encodeURIComponent(doctorId)}/availability?${params.toString()}`,
  );
}

export function getMyWorkingHours(): Promise<WorkingHoursRule[]> {
  return authRequest<WorkingHoursRule[]>("/api/v1/schedule/working-hours");
}

export function updateMyWorkingHours(
  payload: UpdateWorkingHoursPayload,
): Promise<WorkingHoursRule[]> {
  return authRequest<WorkingHoursRule[]>("/api/v1/schedule/working-hours", {
    method: "PUT",
    body: payload,
  });
}
