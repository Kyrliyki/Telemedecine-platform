import { authRequest } from "@/entities/session/api/session";
import type {
  AdminDoctorListQuery,
  DoctorListQuery,
  DoctorProfile,
  PageResponse,
  UpdateProfilePayload,
  UpsertDoctorProfilePayload,
  UserProfile,
} from "@/entities/user/model/types";

function queryString(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return query ? `?${query}` : "";
}

export function fetchMyProfile(): Promise<UserProfile> {
  return authRequest<UserProfile>("/api/v1/users/me");
}

export function updateMyProfile(payload: UpdateProfilePayload): Promise<UserProfile> {
  return authRequest<UserProfile>("/api/v1/users/me", {
    method: "PATCH",
    body: payload,
  });
}

 
export function fetchUserProfile(userId: string): Promise<UserProfile> {
  return authRequest<UserProfile>(`/api/v1/users/${encodeURIComponent(userId)}`);
}

 
export function listDoctors(query: DoctorListQuery = {}): Promise<PageResponse<DoctorProfile>> {
  return authRequest<PageResponse<DoctorProfile>>(
    `/api/v1/users/doctors${queryString({ ...query })}`,
  );
}

 
export function fetchDoctorProfile(userId: string): Promise<DoctorProfile> {
  return authRequest<DoctorProfile>(
    `/api/v1/users/doctors/${encodeURIComponent(userId)}`,
  );
}

 
export function fetchMyDoctorProfile(): Promise<DoctorProfile> {
  return authRequest<DoctorProfile>("/api/v1/users/me/doctor-profile");
}

export function upsertMyDoctorProfile(
  payload: UpsertDoctorProfilePayload,
): Promise<DoctorProfile> {
  return authRequest<DoctorProfile>("/api/v1/users/me/doctor-profile", {
    method: "PUT",
    body: payload,
  });
}

export function listDoctorsForAdmin(
  query: AdminDoctorListQuery = {},
): Promise<PageResponse<DoctorProfile>> {
  return authRequest<PageResponse<DoctorProfile>>(
    `/api/v1/admin/users/doctors${queryString({ ...query })}`,
  );
}

export function approveDoctor(userId: string): Promise<DoctorProfile> {
  return authRequest<DoctorProfile>(
    `/api/v1/admin/users/doctors/${encodeURIComponent(userId)}/approve`,
    { method: "POST" },
  );
}

export function rejectDoctor(userId: string, reason: string): Promise<DoctorProfile> {
  return authRequest<DoctorProfile>(
    `/api/v1/admin/users/doctors/${encodeURIComponent(userId)}/reject`,
    { method: "POST", body: { reason } },
  );
}
