import { ApiError, apiRequest } from "@/shared/api/http";
import {
  clearSession,
  isAccessExpired,
  isRefreshExpired,
  readSession,
  writeSession,
} from "@/entities/session/model/storage";
import type { Session, TokenResponse } from "@/entities/session/model/types";

const SESSION_EXPIRED_MESSAGE = "Сессия истекла. Войдите снова.";

export function toSession(tokens: TokenResponse): Session {
  return {
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    accessExpiresAt: tokens.accessExpiresAt,
    refreshExpiresAt: tokens.refreshExpiresAt,
    mustChangePassword: tokens.mustChangePassword ?? false,
  };
}

let refreshInFlight: Promise<Session> | null = null;

const REFRESH_LOCK_NAME = "telemed.session.refresh";

async function rotate(usedRefreshToken: string, force: boolean): Promise<Session> {
  const current = readSession();
  if (!current) {
    throw new ApiError(401, SESSION_EXPIRED_MESSAGE);
  }
  
  if (current.refreshToken !== usedRefreshToken) {
    return current;
  }
  if (isRefreshExpired(current)) {
    clearSession();
    throw new ApiError(401, SESSION_EXPIRED_MESSAGE);
  }
  
  if (!force && !isAccessExpired(current)) {
    return current;
  }
  if (refreshInFlight) {
    return refreshInFlight;
  }

  refreshInFlight = (async () => {
    try {
      const tokens = await apiRequest<TokenResponse>("/api/v1/auth/refresh", {
        method: "POST",
        body: { refreshToken: usedRefreshToken },
      });
      const next = toSession(tokens);
      writeSession(next);
      return next;
    } catch (error) {
      
      const latest = readSession();
      if (latest && latest.refreshToken !== usedRefreshToken && !isRefreshExpired(latest)) {
        return latest;
      }
      clearSession();
      throw error;
    } finally {
      refreshInFlight = null;
    }
  })();

  return refreshInFlight;
}

export async function refreshSession(force = false): Promise<Session> {
  const current = readSession();
  if (!current) {
    throw new ApiError(401, SESSION_EXPIRED_MESSAGE);
  }
  if (isRefreshExpired(current)) {
    clearSession();
    throw new ApiError(401, SESSION_EXPIRED_MESSAGE);
  }

  const usedRefreshToken = current.refreshToken;
  const locks = typeof navigator !== "undefined" ? navigator.locks : undefined;
  if (locks?.request) {
    return locks.request(REFRESH_LOCK_NAME, () => rotate(usedRefreshToken, force));
  }
  return rotate(usedRefreshToken, force);
}

interface AuthRequestOptions {
  method?: "GET" | "POST" | "PATCH" | "PUT" | "DELETE";
  body?: unknown;
}

export async function authRequest<T>(
  path: string,
  { method = "GET", body }: AuthRequestOptions = {},
): Promise<T> {
  let session = readSession();
  if (!session) {
    throw new ApiError(401, SESSION_EXPIRED_MESSAGE);
  }
  if (isRefreshExpired(session)) {
    clearSession();
    throw new ApiError(401, SESSION_EXPIRED_MESSAGE);
  }
  if (isAccessExpired(session)) {
    session = await refreshSession();
  }

  try {
    return await apiRequest<T>(path, { method, body, accessToken: session.accessToken });
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      const refreshed = await refreshSession(true);
      return apiRequest<T>(path, { method, body, accessToken: refreshed.accessToken });
    }
    throw error;
  }
}

 
export async function logoutSession(): Promise<void> {
  const session = readSession();
  try {
    if (session) {
      await apiRequest<void>("/api/v1/auth/logout", {
        method: "POST",
        body: { refreshToken: session.refreshToken },
        accessToken: isAccessExpired(session) ? undefined : session.accessToken,
      });
    }
  } catch {
    
  } finally {
    clearSession();
  }
}
