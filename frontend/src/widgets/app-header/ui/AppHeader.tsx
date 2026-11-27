import {
  CalendarDays,
  ClipboardCheck,
  ClipboardList,
  HeartPulse,
  History as HistoryIcon,
  LogOut,
  ScrollText,
  ShieldCheck,
  Stethoscope,
  UserRound,
  UsersRound,
} from "lucide-react";
import { useState } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";

import { logoutSession } from "@/entities/session/api/session";
import { rolesFromToken } from "@/entities/session/model/jwt";
import { useSession } from "@/entities/session/model/useSession";
import { cn } from "@/shared/lib/cn";
import { Button } from "@/shared/ui/button";

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "inline-flex items-center gap-2 whitespace-nowrap rounded-md px-3 py-2 text-sm font-medium transition-colors",
    isActive
      ? "bg-secondary text-secondary-foreground"
      : "text-muted-foreground hover:text-foreground",
  );

export function AppHeader() {
  const session = useSession();
  const navigate = useNavigate();
  const [loggingOut, setLoggingOut] = useState(false);
  const roles = session ? rolesFromToken(session.accessToken) : [];
  const isAdmin = roles.includes("ADMIN");
  const isDoctor = roles.includes("DOCTOR");

  async function onLogout() {
    setLoggingOut(true);
    try {
      await logoutSession();
    } finally {
      navigate("/login", { replace: true });
    }
  }

  return (
    <header className="border-b bg-card">
      <div className="mx-auto flex min-h-16 max-w-6xl flex-wrap items-center justify-between gap-x-4 gap-y-2 px-6 py-2">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
          <Link to="/" className="flex items-center gap-3 font-semibold">
            <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary text-primary-foreground">
              <HeartPulse className="h-4 w-4" />
            </span>
            Telemed
          </Link>

          <nav className="flex flex-wrap items-center gap-1">
            <NavLink to="/doctors" className={navLinkClass}>
              <Stethoscope className="h-4 w-4" />
              Врачи
            </NavLink>
            <NavLink to="/history" className={navLinkClass}>
              <HistoryIcon className="h-4 w-4" />
              История
            </NavLink>
            {roles.includes("PATIENT") || roles.includes("DOCTOR") ? (
              <NavLink to="/calendar" className={navLinkClass}>
                <CalendarDays className="h-4 w-4" />
                Календарь
              </NavLink>
            ) : null}
            {roles.includes("PATIENT") || roles.includes("DOCTOR") ? (
              <NavLink to="/medical-records" className={navLinkClass}>
                <ClipboardList className="h-4 w-4" />
                Медкнижки
              </NavLink>
            ) : null}
            <NavLink to="/profile" className={navLinkClass}>
              <UserRound className="h-4 w-4" />
              Профиль
            </NavLink>
            {isDoctor ? (
              <NavLink to="/doctor/profile" className={navLinkClass}>
                <ClipboardCheck className="h-4 w-4" />
                Кабинет врача
              </NavLink>
            ) : null}
            {isAdmin ? (
              <>
                <NavLink to="/admin/users" className={navLinkClass}>
                  <UsersRound className="h-4 w-4" />
                  Пользователи
                </NavLink>
                <NavLink to="/admin/doctors" className={navLinkClass}>
                  <ShieldCheck className="h-4 w-4" />
                  Верификация
                </NavLink>
                <NavLink to="/admin/audit" className={navLinkClass}>
                  <ScrollText className="h-4 w-4" />
                  Аудит входа
                </NavLink>
              </>
            ) : null}
          </nav>
        </div>

        <div className="flex items-center gap-2">
          <Button variant="ghost" onClick={onLogout} disabled={loggingOut}>
            <LogOut className="h-4 w-4" />
            {loggingOut ? "Выходим…" : "Выйти"}
          </Button>
        </div>
      </div>
    </header>
  );
}
