import { authRequest } from "@/entities/session/api/session";
import type { MedicalRecord, MedicalRecordPage } from "@/entities/emr/model/types";

export interface MedicalRecordListQuery {
  page?: number;
  size?: number;
  status?: string;
  view?: "patient" | "doctor";
}

function queryString(params: MedicalRecordListQuery): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return query ? `?${query}` : "";
}

export function listMyMedicalRecords(
  query: MedicalRecordListQuery = {},
): Promise<MedicalRecordPage> {
  return authRequest<MedicalRecordPage>(`/api/v1/emr/me/records${queryString(query)}`);
}

export function listPatientMedicalRecords(
  patientId: string,
  query: MedicalRecordListQuery = {},
): Promise<MedicalRecordPage> {
  return authRequest<MedicalRecordPage>(
    `/api/v1/emr/patients/${encodeURIComponent(patientId)}/records${queryString(query)}`,
  );
}

export function getMedicalRecord(recordId: string): Promise<MedicalRecord> {
  return authRequest<MedicalRecord>(`/api/v1/emr/records/${encodeURIComponent(recordId)}`);
}

export interface CreateMedicalRecordPayload {
  patientId: string;
  appointmentId?: string;
  doctorId?: string;
  chiefComplaint?: string;
  diagnosisCode?: string;
  diagnosisText?: string;
  notes?: string;
  treatmentPlan?: string;
}

export interface UpdateMedicalRecordPayload {
  chiefComplaint?: string;
  diagnosisCode?: string;
  diagnosisText?: string;
  notes?: string;
  treatmentPlan?: string;
}

export interface VitalSignsPayload {
  heightCm?: number;
  weightKg?: number;
  systolic?: number;
  diastolic?: number;
  pulse?: number;
  temperatureC?: number;
  spo2?: number;
}

export function createMedicalRecord(
  payload: CreateMedicalRecordPayload,
): Promise<MedicalRecord> {
  return authRequest<MedicalRecord>("/api/v1/emr/records", {
    method: "POST",
    body: payload,
  });
}

export function updateMedicalRecord(
  recordId: string,
  payload: UpdateMedicalRecordPayload,
): Promise<MedicalRecord> {
  return authRequest<MedicalRecord>(
    `/api/v1/emr/records/${encodeURIComponent(recordId)}`,
    { method: "PATCH", body: payload },
  );
}

export function upsertMedicalVitals(
  recordId: string,
  payload: VitalSignsPayload,
): Promise<MedicalRecord> {
  return authRequest<MedicalRecord>(
    `/api/v1/emr/records/${encodeURIComponent(recordId)}/vitals`,
    { method: "PUT", body: payload },
  );
}

export function finalizeMedicalRecord(recordId: string): Promise<MedicalRecord> {
  return authRequest<MedicalRecord>(
    `/api/v1/emr/records/${encodeURIComponent(recordId)}/finalize`,
    { method: "POST" },
  );
}
