export type ScheduleSlotStatus = "AVAILABLE" | "HELD" | "CONFIRMED" | string;

export interface ScheduleSlot {
   
  id: string | null;
  doctorId: string;
  startsAt: string;
  endsAt: string;
  status: ScheduleSlotStatus;
  appointmentId: string | null;
  holdExpiresAt: string | null;
  slotType: string | null;
  location: string | null;
}

export interface WorkingHoursRule {
  id: string;
  doctorId: string;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  slotDurationMinutes: number;
  bufferMinutes: number;
  timezone: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  slotType: string | null;
  location: string | null;
}

export interface WorkingHoursEntryPayload {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  slotDurationMinutes: number;
  bufferMinutes: number;
  timezone: string;
  slotType: string | null;
  location: string | null;
}

export interface UpdateWorkingHoursPayload {
  entries: WorkingHoursEntryPayload[];
  effectiveFrom: string;
  effectiveTo: string | null;
}
