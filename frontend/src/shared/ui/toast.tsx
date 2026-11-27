import { AlertCircle, CheckCircle2, Info, X } from "lucide-react";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { createPortal } from "react-dom";

import { cn } from "@/shared/lib/cn";

export type ToastVariant = "success" | "error" | "info";

export interface ToastInput {
  title?: string;
  description: string;
  variant?: ToastVariant;
  durationMs?: number;
}

interface ToastItem extends ToastInput {
  id: number;
}

interface ToastContextValue {
  showToast: (toast: ToastInput) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

const VARIANT_STYLES: Record<ToastVariant, string> = {
  success: "border-emerald-200 bg-emerald-50 text-emerald-900",
  error: "border-destructive/30 bg-destructive/10 text-destructive",
  info: "border-border bg-card text-card-foreground",
};

const VARIANT_ICONS: Record<ToastVariant, typeof Info> = {
  success: CheckCircle2,
  error: AlertCircle,
  info: Info,
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const nextId = useRef(0);
  const timers = useRef(new Map<number, number>());

  const dismiss = useCallback((id: number) => {
    const timer = timers.current.get(id);
    if (timer !== undefined) {
      window.clearTimeout(timer);
      timers.current.delete(id);
    }
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const showToast = useCallback(
    (input: ToastInput) => {
      const id = nextId.current + 1;
      nextId.current = id;
      const toast: ToastItem = {
        id,
        variant: "success",
        durationMs: 5000,
        ...input,
      };

      setToasts((current) => [...current.slice(-3), toast]);
      const timer = window.setTimeout(
        () => dismiss(id),
        Math.max(2000, toast.durationMs ?? 5000),
      );
      timers.current.set(id, timer);
    },
    [dismiss],
  );

  useEffect(() => {
    const pending = timers.current;
    return () => {
      pending.forEach((timer) => window.clearTimeout(timer));
      pending.clear();
    };
  }, []);

  const value = useMemo(() => ({ showToast }), [showToast]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      {createPortal(
        <div
          aria-live="polite"
          className="pointer-events-none fixed right-4 top-4 z-[100] flex w-[calc(100vw-2rem)] max-w-sm flex-col gap-2"
        >
          {toasts.map((toast) => {
            const variant = toast.variant ?? "success";
            const Icon = VARIANT_ICONS[variant];
            return (
              <div
                key={toast.id}
                role={variant === "error" ? "alert" : "status"}
                className={cn(
                  "pointer-events-auto flex items-start gap-3 rounded-lg border p-4 shadow-lg animate-in fade-in-0 slide-in-from-right-5",
                  VARIANT_STYLES[variant],
                )}
              >
                <Icon className="mt-0.5 h-4 w-4 shrink-0" />
                <div className="min-w-0 flex-1 space-y-0.5">
                  {toast.title ? <p className="text-sm font-medium">{toast.title}</p> : null}
                  <p className="text-sm opacity-90">{toast.description}</p>
                </div>
                <button
                  type="button"
                  onClick={() => dismiss(toast.id)}
                  className="shrink-0 rounded-md p-1 opacity-70 transition-opacity hover:opacity-100"
                  aria-label="Закрыть уведомление"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            );
          })}
        </div>,
        document.body,
      )}
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast must be used inside ToastProvider");
  }
  return context;
}
