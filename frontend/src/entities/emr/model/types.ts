export type MedicalRecordStatus = "DRAFT" | "FINALIZED" | "AMENDED" | "DELETED";

export interface MedicalPrescription {
  id: string;
  medicationName: string;
  dosage: string;
  frequency: string;
  durationDays: number;
  instructions: string | null;
  createdAt: string;
}

export interface MedicalVitalSigns {
  id: string;
  heightCm: number | null;
  weightKg: number | null;
  systolic: number | null;
  diastolic: number | null;
  pulse: number | null;
  temperatureC: number | null;
  spo2: number | null;
  recordedAt: string;
}

export interface MedicalAttachment {
  id: string;
  fileId: string;
  description: string | null;
  createdAt: string;
}

export interface MedicalRecord {
  id: string;
  patientId: string;
  doctorId: string;
  appointmentId: string | null;
  status: MedicalRecordStatus;
  chiefComplaint: string | null;
  diagnosisCode: string | null;
  diagnosisText: string | null;
  notes: string | null;
  treatmentPlan: string | null;
  createdAt: string;
  updatedAt: string;
  finalizedAt: string | null;
  version: number;
  prescriptions: MedicalPrescription[];
  vitalSigns: MedicalVitalSigns | null;
  attachments: MedicalAttachment[];
}

export interface MedicalRecordPage {
  content: MedicalRecord[];
  totalElements: number;
  page: number;
  size: number;
  totalPages: number;
}
