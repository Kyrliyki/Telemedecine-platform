import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";

import { sendChatNotice } from "@/entities/chat/api/chat";
import type { JoinCallResult } from "@/entities/video/model/types";
import { CallDialog } from "@/features/video-call/ui/CallDialog";

interface ActiveCallState {
  join: JoinCallResult;
  counterpartName: string;
  canEnd: boolean;
}

interface StartCallOptions {
  counterpartName: string;
  canEnd: boolean;
}

interface CallContextValue {
  activeCall: JoinCallResult | null;
  startCall: (join: JoinCallResult, options: StartCallOptions) => void;
  closeCall: () => void;
}

const CallContext = createContext<CallContextValue | null>(null);

export function CallProvider({ children }: { children: ReactNode }) {
  const [activeCall, setActiveCall] = useState<ActiveCallState | null>(null);

  const startCall = useCallback((join: JoinCallResult, options: StartCallOptions) => {
    setActiveCall({
      join,
      counterpartName: options.counterpartName,
      canEnd: options.canEnd,
    });
  }, []);

  const closeCall = useCallback(() => setActiveCall(null), []);

  const value = useMemo(
    () => ({
      activeCall: activeCall?.join ?? null,
      startCall,
      closeCall,
    }),
    [activeCall, startCall, closeCall],
  );

  return (
    <CallContext.Provider value={value}>
      {children}
      <CallDialog
        open={Boolean(activeCall)}
        join={activeCall?.join ?? null}
        counterpartName={activeCall?.counterpartName ?? "Собеседник"}
        canEnd={activeCall?.canEnd ?? false}
        onClose={closeCall}
        onEnded={() => {
          if (!activeCall) {
            return;
          }
          void sendChatNotice(
            activeCall.join.call.appointment_id,
            "Видеозвонок завершён",
          ).catch(() => undefined);
        }}
      />
    </CallContext.Provider>
  );
}

export function useCall(): CallContextValue {
  const context = useContext(CallContext);
  if (!context) {
    throw new Error("useCall must be used inside CallProvider");
  }
  return context;
}
