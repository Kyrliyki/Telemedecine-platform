import { authRequest } from "@/entities/session/api/session";
import type { CurrentUser } from "@/entities/session/model/types";

interface UserProfileResponse {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  roles: string[];
  status: string;
}

 
export async function fetchCurrentUser(): Promise<CurrentUser> {
  const profile = await authRequest<UserProfileResponse>("/api/v1/users/me");

  return {
    id: profile.id,
    email: profile.email,
    firstName: profile.firstName,
    lastName: profile.lastName,
    roles: profile.roles ?? [],
    status: profile.status,
  };
}
