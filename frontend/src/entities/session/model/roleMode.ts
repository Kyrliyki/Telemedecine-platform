import { useEffect, useState } from "react";

export type RoleMode = "patient" | "doctor";

const STORAGE_KEY = "telemed.roleMode";
const listeners = new Set<() => void>();

function isRoleMode(value: string | null): value is RoleMode {
  return value === "patient" || value === "doctor";
}

export function hasRole(roles: string[] | null | undefined, role: string): boolean {
  return roles?.includes(role) ?? false;
}

export function isDualRole(roles: string[] | null | undefined): boolean {
  return hasRole(roles, "PATIENT") && hasRole(roles, "DOCTOR");
}

export function defaultRoleMode(roles: string[] | null | undefined): RoleMode {
  const isDoctor = hasRole(roles, "DOCTOR");
  const isPatient = hasRole(roles, "PATIENT");

  if (isDoctor && !isPatient) {
    return "doctor";
  }
  if (isPatient && !isDoctor) {
    return "patient";
  }
  if (typeof window !== "undefined") {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    if (isRoleMode(stored)) {
      return stored;
    }
  }
  return "patient";
}

export function useRoleMode(
  roles: string[] | null | undefined,
): readonly [RoleMode, (mode: RoleMode) => void] {
  const rolesKey = (roles ?? []).join(",");
  const [mode, setModeState] = useState<RoleMode>(() => defaultRoleMode(roles));

  useEffect(() => {
    const sync = () => setModeState(defaultRoleMode(roles));
    sync();
    listeners.add(sync);
    return () => {
      listeners.delete(sync);
    };
    
    
  }, [rolesKey]);

  function setMode(next: RoleMode) {
    if (typeof window !== "undefined" && isDualRole(roles)) {
      window.localStorage.setItem(STORAGE_KEY, next);
    }
    setModeState(next);
    listeners.forEach((listener) => listener());
  }

  return [mode, setMode] as const;
}
