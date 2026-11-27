import { useEffect, useState } from "react";

import { SESSION_STORAGE_KEY } from "@/shared/config/env";
import { subscribeSession } from "@/entities/session/model/events";
import { readSession } from "@/entities/session/model/storage";
import type { Session } from "@/entities/session/model/types";

 
export function useSession(): Session | null {
  const [session, setSession] = useState<Session | null>(() => readSession());

  useEffect(() => {
    const update = () => setSession(readSession());
    const unsubscribe = subscribeSession(update);
    const onStorage = (event: StorageEvent) => {
      if (event.key === SESSION_STORAGE_KEY) {
        update();
      }
    };
    window.addEventListener("storage", onStorage);
    return () => {
      unsubscribe();
      window.removeEventListener("storage", onStorage);
    };
  }, []);

  return session;
}
