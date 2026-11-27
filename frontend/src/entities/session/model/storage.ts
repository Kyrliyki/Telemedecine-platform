import { SESSION_STORAGE_KEY } from "@/shared/config/env";
import { emitSessionChanged } from "@/entities/session/model/events";
import type { Session } from "@/entities/session/model/types";

const ACCESS_EXPIRY_SKEW_MS = 5_000;

let cachedRaw: string | null | undefined;
let cachedSession: Session | null = null;

function parse(raw: string | null): Session | null {
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as Partial<Session>;
    if (!parsed.accessToken || !parsed.refreshToken) {
      return null;
    }
    return {
      accessToken: parsed.accessToken,
      refreshToken: parsed.refreshToken,
      accessExpiresAt: parsed.accessExpiresAt ?? "",
      refreshExpiresAt: parsed.refreshExpiresAt ?? "",
      mustChangePassword: parsed.mustChangePassword ?? false,
    };
  } catch {
    return null;
  }
}

export function readSession(): Session | null {
  const raw = window.localStorage.getItem(SESSION_STORAGE_KEY);
  if (raw === cachedRaw) {
    return cachedSession;
  }
  cachedRaw = raw;
  cachedSession = parse(raw);
  return cachedSession;
}

export function writeSession(session: Session): void {
  const raw = JSON.stringify(session);
  window.localStorage.setItem(SESSION_STORAGE_KEY, raw);
  cachedRaw = raw;
  cachedSession = session;
  emitSessionChanged();
}

export function clearSession(): void {
  window.localStorage.removeItem(SESSION_STORAGE_KEY);
  cachedRaw = null;
  cachedSession = null;
  emitSessionChanged();
}

export function isAccessExpired(session: Session, skewMs = ACCESS_EXPIRY_SKEW_MS): boolean {
  if (!session.accessExpiresAt) {
    return false;
  }
  const expiresAt = Date.parse(session.accessExpiresAt);
  return Number.isFinite(expiresAt) && expiresAt - skewMs <= Date.now();
}

export function isRefreshExpired(session: Session): boolean {
  if (!session.refreshExpiresAt) {
    return false;
  }
  const expiresAt = Date.parse(session.refreshExpiresAt);
  return Number.isFinite(expiresAt) && expiresAt <= Date.now();
}

export function hasActiveSession(session: Session | null): session is Session {
  return session !== null && !isRefreshExpired(session);
}
