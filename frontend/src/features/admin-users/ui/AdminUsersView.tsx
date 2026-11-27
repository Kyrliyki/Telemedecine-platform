import { AlertCircle, CheckCircle2, Loader2, MailPlus, Plus, RefreshCw } from "lucide-react";
import { FormEvent, useCallback, useEffect, useState } from "react";

import {
  assignRole,
  blockUser,
  inviteUser,
  listAdminUsers,
  revokeRole,
  unblockUser,
  type AdminUser,
  type InvitationResult,
} from "@/features/admin-users/api/adminUsers";
import { formatApiError } from "@/shared/api/http";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/ui/card";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";
import { useToast } from "@/shared/ui/toast";

const ROLE_OPTIONS = ["DOCTOR", "PATIENT", "ADMIN"];
const STATUS_OPTIONS = [
  "ACTIVE",
  "PENDING_EMAIL_VERIFICATION",
  "PENDING_INVITATION",
  "BLOCKED",
];

const roleLabels: Record<string, string> = {
  ADMIN: "Администратор",
  DOCTOR: "Врач",
  PATIENT: "Пациент",
};

const statusLabels: Record<string, string> = {
  ACTIVE: "Активен",
  PENDING_EMAIL_VERIFICATION: "Ждёт подтверждения email",
  PENDING_INVITATION: "Ждёт принятия приглашения",
  BLOCKED: "Заблокирован",
};

const PAGE_SIZE = 20;

export function AdminUsersView() {
  const { showToast } = useToast();
  const [filters, setFilters] = useState({ email: "", status: "", role: "" });
  const [appliedFilters, setAppliedFilters] = useState(filters);
  const [page, setPage] = useState(0);
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [busyUserId, setBusyUserId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setError(null);
      try {
        const result = await listAdminUsers({
          page,
          size: PAGE_SIZE,
          email: appliedFilters.email.trim() || undefined,
          status: appliedFilters.status || undefined,
          role: appliedFilters.role || undefined,
        });
        if (!cancelled) {
          setUsers(result.content ?? []);
          setTotalElements(result.totalElements ?? 0);
          setTotalPages(result.totalPages ?? 0);
        }
      } catch (requestError) {
        if (!cancelled) {
          setError(formatApiError(requestError));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, [appliedFilters, page, reloadKey]);

  const reload = useCallback(() => setReloadKey((value) => value + 1), []);

  function applyFilters(event: FormEvent) {
    event.preventDefault();
    setPage(0);
    setAppliedFilters(filters);
  }

  function resetFilters() {
    const empty = { email: "", status: "", role: "" };
    setFilters(empty);
    setAppliedFilters(empty);
    setPage(0);
  }

  async function runAction(userId: string, message: string, action: () => Promise<void>) {
    setBusyUserId(userId);
    setError(null);
    try {
      await action();
      showToast({ description: message, variant: "success" });
      reload();
    } catch (requestError) {
      setError(formatApiError(requestError));
    } finally {
      setBusyUserId(null);
    }
  }

  function onBlock(user: AdminUser) {
    const reason = window.prompt(
      `Причина блокировки ${user.email}`,
      "Заблокирован администратором",
    );
    if (reason === null) {
      return;
    }
    void runAction(user.userId, `${user.email} заблокирован`, () =>
      blockUser(user.userId, reason.trim() || undefined),
    );
  }

  function onRevokeRole(user: AdminUser, role: string) {
    if (!window.confirm(`Отозвать роль ${roleLabels[role] ?? role} у ${user.email}?`)) {
      return;
    }
    void runAction(user.userId, `Роль ${role} отозвана`, () =>
      revokeRole(user.userId, role),
    );
  }

  return (
    <div className="space-y-6">
      <InviteForm
        onInvited={() => {
          showToast({ description: "Приглашение создано.", variant: "success" });
          reload();
        }}
      />

      <Card>
        <CardHeader className="space-y-3">
          <div>
            <CardTitle className="text-xl">Пользователи</CardTitle>
            <CardDescription>
              Всего: {totalElements}
            </CardDescription>
          </div>

          <form className="grid gap-3 sm:grid-cols-[1fr_180px_160px_auto]" onSubmit={applyFilters}>
            <div className="space-y-1">
              <Label htmlFor="filter-email" className="text-xs">
                Email
              </Label>
              <Input
                id="filter-email"
                value={filters.email}
                onChange={(event) => setFilters({ ...filters, email: event.target.value })}
                placeholder="user@example.com"
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="filter-status" className="text-xs">
                Статус
              </Label>
              <select
                id="filter-status"
                className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={filters.status}
                onChange={(event) => setFilters({ ...filters, status: event.target.value })}
              >
                <option value="">Любой</option>
                {STATUS_OPTIONS.map((status) => (
                  <option key={status} value={status}>
                    {statusLabels[status] ?? status}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <Label htmlFor="filter-role" className="text-xs">
                Роль
              </Label>
              <select
                id="filter-role"
                className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={filters.role}
                onChange={(event) => setFilters({ ...filters, role: event.target.value })}
              >
                <option value="">Любая</option>
                {ROLE_OPTIONS.map((role) => (
                  <option key={role} value={role}>
                    {roleLabels[role] ?? role}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex items-end gap-2">
              <Button type="submit">Найти</Button>
              <Button type="button" variant="outline" onClick={resetFilters}>
                Сбросить
              </Button>
            </div>
          </form>
        </CardHeader>

        <CardContent className="space-y-4">
          {error ? (
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}

          {loading ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" />
              Загружаем пользователей
            </div>
          ) : users.length === 0 ? (
            <p className="text-sm text-muted-foreground">Пользователи не найдены.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[880px] text-sm">
                <thead>
                  <tr className="border-b text-left text-xs uppercase tracking-wide text-muted-foreground">
                    <th className="pb-2 pr-3 font-medium">Email</th>
                    <th className="pb-2 pr-3 font-medium">Статус</th>
                    <th className="pb-2 pr-3 font-medium">Роли</th>
                    <th className="pb-2 pr-3 font-medium">Создан</th>
                    <th className="pb-2 font-medium">Действия</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((user) => (
                    <UserRow
                      key={user.userId}
                      user={user}
                      busy={busyUserId === user.userId}
                      onBlock={onBlock}
                      onUnblock={(target) =>
                        void runAction(target.userId, `${target.email} разблокирован`, () =>
                          unblockUser(target.userId),
                        )
                      }
                      onRevokeRole={onRevokeRole}
                      onAssignRole={(target, role) =>
                        void runAction(
                          target.userId,
                          `Роль ${role} выдана пользователю ${target.email}`,
                          () => assignRole(target.userId, role),
                        )
                      }
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="flex items-center justify-between gap-3">
            <span className="text-xs text-muted-foreground">
              Страница {totalPages === 0 ? 0 : page + 1} из {totalPages}
            </span>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0 || loading}
                onClick={() => setPage((value) => Math.max(0, value - 1))}
              >
                Назад
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={page + 1 >= totalPages || loading}
                onClick={() => setPage((value) => value + 1)}
              >
                Вперёд
              </Button>
              <Button variant="ghost" size="sm" disabled={loading} onClick={reload}>
                <RefreshCw className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

interface InviteFormProps {
  onInvited: () => void;
}

function InviteForm({ onInvited }: InviteFormProps) {
  const [email, setEmail] = useState("");
  const [roles, setRoles] = useState<string[]>(["DOCTOR"]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<InvitationResult | null>(null);

  function toggleRole(role: string) {
    setRoles((current) =>
      current.includes(role) ? current.filter((item) => item !== role) : [...current, role],
    );
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setResult(null);
    if (roles.length === 0) {
      setError("Выберите хотя бы одну роль.");
      return;
    }
    setSubmitting(true);
    try {
      const invitation = await inviteUser(email.trim(), roles);
      setResult(invitation);
      setEmail("");
      onInvited();
    } catch (requestError) {
      setError(formatApiError(requestError));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-xl">
          <MailPlus className="h-5 w-5 text-primary" />
          Пригласить пользователя
        </CardTitle>
      </CardHeader>
      <CardContent>
        <form className="grid gap-4 sm:grid-cols-[1fr_auto]" onSubmit={onSubmit}>
          <div className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="invite-email" className="text-xs">
                Email
              </Label>
              <Input
                id="invite-email"
                type="email"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="doctor@example.com"
              />
            </div>
            <div className="space-y-2">
              <span className="text-xs font-medium text-muted-foreground">Роли</span>
              <div className="flex flex-wrap gap-3">
                {ROLE_OPTIONS.map((role) => (
                  <label key={role} className="flex items-center gap-2 text-sm">
                    <input
                      type="checkbox"
                      className="h-4 w-4 rounded border-input"
                      checked={roles.includes(role)}
                      onChange={() => toggleRole(role)}
                    />
                    {roleLabels[role] ?? role}
                  </label>
                ))}
              </div>
            </div>
          </div>
          <div className="flex items-end">
            <Button type="submit" disabled={submitting}>
              {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
              Пригласить
            </Button>
          </div>
        </form>

        {error ? (
          <p className="mt-3 flex items-start gap-2 text-sm text-destructive">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
            {error}
          </p>
        ) : null}

        {result ? (
          <p className="mt-3 flex items-start gap-2 text-sm text-emerald-700">
            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
            Приглашение для {result.email} создано, действует до{" "}
            {new Date(result.expiresAt).toLocaleString("ru-RU")}.
          </p>
        ) : null}
      </CardContent>
    </Card>
  );
}

interface UserRowProps {
  user: AdminUser;
  busy: boolean;
  onBlock: (user: AdminUser) => void;
  onUnblock: (user: AdminUser) => void;
  onRevokeRole: (user: AdminUser, role: string) => void;
  onAssignRole: (user: AdminUser, role: string) => void;
}

function UserRow({
  user,
  busy,
  onBlock,
  onUnblock,
  onRevokeRole,
  onAssignRole,
}: UserRowProps) {
  const [roleToAdd, setRoleToAdd] = useState(ROLE_OPTIONS[0]);
  const availableRoles = ROLE_OPTIONS.filter((role) => !user.roles.includes(role));

  useEffect(() => {
    if (availableRoles.length > 0 && !availableRoles.includes(roleToAdd)) {
      setRoleToAdd(availableRoles[0]);
    }
  }, [availableRoles, roleToAdd]);

  return (
    <tr className="border-b align-top last:border-b-0">
      <td className="py-3 pr-3">
        <div className="font-medium">{user.email}</div>
        <div className="text-xs text-muted-foreground">
          {user.emailVerified ? "email подтверждён" : "email не подтверждён"}
        </div>
      </td>
      <td className="py-3 pr-3">
        <span className="rounded-full bg-secondary px-2 py-1 text-xs font-medium">
          {statusLabels[user.status] ?? user.status}
        </span>
      </td>
      <td className="py-3 pr-3">
        <div className="flex flex-wrap gap-1">
          {user.roles.map((role) => (
            <span
              key={role}
              className="inline-flex items-center gap-1 rounded-full bg-secondary px-2 py-0.5 text-xs"
            >
              {roleLabels[role] ?? role}
              <button
                type="button"
                className="text-muted-foreground hover:text-destructive"
                title="Отозвать роль"
                disabled={busy}
                onClick={() => onRevokeRole(user, role)}
              >
                ×
              </button>
            </span>
          ))}
        </div>
        {availableRoles.length > 0 ? (
          <div className="mt-2 flex items-center gap-1">
            <select
              className="h-8 rounded-md border border-input bg-background px-2 text-xs"
              value={roleToAdd}
              onChange={(event) => setRoleToAdd(event.target.value)}
              disabled={busy}
            >
              {availableRoles.map((role) => (
                <option key={role} value={role}>
                  {roleLabels[role] ?? role}
                </option>
              ))}
            </select>
            <Button
              type="button"
              size="sm"
              variant="outline"
              className="h-8"
              disabled={busy}
              onClick={() => onAssignRole(user, roleToAdd)}
            >
              <Plus className="h-3 w-3" />
            </Button>
          </div>
        ) : null}
      </td>
      <td className="py-3 pr-3 text-xs text-muted-foreground">
        {formatDate(user.createdAt)}
      </td>
      <td className="py-3">
        <div className="flex flex-wrap gap-1">
          {user.status === "BLOCKED" ? (
            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={busy}
              onClick={() => onUnblock(user)}
            >
              Разблокировать
            </Button>
          ) : (
            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={busy}
              onClick={() => onBlock(user)}
            >
              Блокировать
            </Button>
          )}
        </div>
      </td>
    </tr>
  );
}

function formatDate(value: string): string {
  if (!value) {
    return "—";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("ru-RU", { dateStyle: "short", timeStyle: "short" }).format(
    date,
  );
}
