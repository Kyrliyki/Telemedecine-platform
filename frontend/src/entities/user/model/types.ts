export interface DoctorProfile {
  userId: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  specialization: string;
  bio: string | null;
  yearsOfExperience: number;
  licenseNumber: string | null;
  acceptingPatients: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
  verificationStatus: string;
  verifiedAt: string | null;
  verifiedBy: string | null;
  rejectionReason: string | null;
}

export interface UserProfile {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  roles: string[];
  status: string;
  createdAt: string;
  updatedAt: string;
  lastSeenAt: string | null;
  version: number;
  doctorProfile: DoctorProfile | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface UpdateProfilePayload {
  firstName: string | null;
  lastName: string | null;
}

export interface UpsertDoctorProfilePayload {
  specialization: string;
  bio: string | null;
  yearsOfExperience: number;
  licenseNumber: string | null;
  acceptingPatients: boolean;
}

export interface DoctorListQuery {
  page?: number;
  size?: number;
  specialization?: string;
}

export interface AdminDoctorListQuery extends DoctorListQuery {
  verificationStatus?: string;
}
