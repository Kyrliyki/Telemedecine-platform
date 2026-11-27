export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");

export const SESSION_STORAGE_KEY = "telemed.session";

export const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID ?? "";

export const GOOGLE_REDIRECT_URI =
  import.meta.env.VITE_GOOGLE_REDIRECT_URI ??
  "http://localhost:8081/api/v1/auth/oauth/google/callback";

export const OAUTH_STATE_KEY = "telemed.oauth.state";

export const OAUTH_NEXT_KEY = "telemed.oauth.next";
