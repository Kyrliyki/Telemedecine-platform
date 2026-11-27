 
export function safeNextPath(raw: string | null | undefined, fallback = "/"): string {
  if (!raw) {
    return fallback;
  }
  const value = raw.trim();
  if (!value.startsWith("/") || value.startsWith("//")) {
    return fallback;
  }
  if (value.startsWith("/login") || value.startsWith("/register")) {
    return fallback;
  }
  return value;
}
