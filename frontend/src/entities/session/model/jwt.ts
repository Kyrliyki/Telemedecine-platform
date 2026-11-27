export interface JwtPayload {
  sub?: string;
  exp?: number;
  iat?: number;
  roles?: string[];
  email?: string;
  email_verified?: boolean;
}

export function readJwtPayload(token: string): JwtPayload | null {
  const parts = token.split(".");
  if (parts.length < 2) {
    return null;
  }
  try {
    const normalized = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    const bytes = Uint8Array.from(atob(padded), (char) => char.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes)) as JwtPayload;
  } catch {
    return null;
  }
}

export function rolesFromToken(token: string): string[] {
  return readJwtPayload(token)?.roles ?? [];
}

export function hasRole(token: string, role: string): boolean {
  return rolesFromToken(token).includes(role);
}
