import type { RoleMode } from "@/entities/session/model/roleMode";
import { cn } from "@/shared/lib/cn";

interface RoleModeSwitchProps {
  mode: RoleMode;
  onChange: (mode: RoleMode) => void;
  className?: string;
}

const OPTIONS: Array<{ value: RoleMode; label: string }> = [
  { value: "patient", label: "Я пациент" },
  { value: "doctor", label: "Я врач" },
];

export function RoleModeSwitch({ mode, onChange, className }: RoleModeSwitchProps) {
  return (
    <div className={cn("inline-flex rounded-lg border bg-secondary/40 p-1", className)}>
      {OPTIONS.map((option) => (
        <button
          key={option.value}
          type="button"
          onClick={() => onChange(option.value)}
          className={cn(
            "rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
            mode === option.value
              ? "bg-card shadow-sm"
              : "text-muted-foreground hover:text-foreground",
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
