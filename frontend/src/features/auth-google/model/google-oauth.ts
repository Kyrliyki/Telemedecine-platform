import {
  GOOGLE_CLIENT_ID,
  GOOGLE_REDIRECT_URI,
  OAUTH_NEXT_KEY,
  OAUTH_STATE_KEY,
} from "@/shared/config/env";
import { safeNextPath } from "@/shared/lib/redirect";

export function startGoogleLogin(next?: string | null): void {
  if (!GOOGLE_CLIENT_ID) {
    return;
  }

  const state =
    typeof crypto !== "undefined" && "randomUUID" in crypto
      ? crypto.randomUUID()
      : Math.random().toString(36).slice(2) + Date.now().toString(36);

  window.sessionStorage.setItem(OAUTH_STATE_KEY, state);
  window.sessionStorage.setItem(OAUTH_NEXT_KEY, safeNextPath(next));

  const params = new URLSearchParams({
    client_id: GOOGLE_CLIENT_ID,
    redirect_uri: GOOGLE_REDIRECT_URI,
    response_type: "code",
    scope: "openid email profile",
    state,
    prompt: "select_account",
  });

  window.location.assign(`https://accounts.google.com/o/oauth2/v2/auth?${params.toString()}`);
}
