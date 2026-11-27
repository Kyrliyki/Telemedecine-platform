import { AlertCircle, Loader2, RefreshCw, ShieldCheck } from "lucide-react";
import { FormEvent, useCallback, useEffect, useState } from "react";

import {
  searchAuthAudit,
  type AuthAuditEntry,
  type AuthAuditPage,
} from "@/features/admin-audit/api/adminAudit";
import { formatApiError } from "@/shared/api/http";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/ui/card";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";

const PAGE_SIZE = 20;
const EMPTY_PAGE: AuthAuditPage = { content: [], totalElements: 0, page: 0, size: PAGE_SIZE, totalPages: 0 };

export function AdminAuditView() {
  const [filters, setFilters] = useState({ action: "", targetUserId: "" });
  const [appliedFilters, setAppliedFilters] = useState(filters);
  const [page, setPage] = useState(0);
  const [data, setData] = useState<AuthAuditPage>(EMPTY_PAGE);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setError(null);
      try {
        const result = await searchAuthAudit({
          action: appliedFilters.action.trim().toUpperCase() || undefined,
          targetUserId: appliedFilters.targetUserId.trim() || undefined,
          page,
          size: PAGE_SIZE,
        });
        if (!cancelled) {
          setData(result);
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
    const empty = { action: "", targetUserId: "" };
    setFilters(empty);
    setAppliedFilters(empty);
    setPage(0);
  }

  return (
    <Card>
      <CardHeader className="space-y-3">
        <div>
          <CardTitle className="flex items-center gap-2 text-xl">
            <ShieldCheck className="h-5 w-5 text-primary" />
            Аудит аутентификации
          </CardTitle>
          <CardDescription>
            Входы, смена ролей, блокировки и привязки OAuth. Всего записей:{" "}
            {data.totalElements}.
          </CardDescription>
        </div>

        <form className="grid gap-3 sm:grid-cols-[200px_1fr_auto]" onSubmit={applyFilters}>
          <div className="space-y-1">
            <Label htmlFor="audit-action" className="text-xs">
              Действие
            </Label>
            <Input
              id="audit-action"
              value={filters.action}
              onChange={(event) => setFilters({ ...filters, action: event.target.value })}
              placeholder="LOGIN_SUCCESS"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="audit-target" className="text-xs">
              ID пользователя
            </Label>
            <Input
              id="audit-target"
              value={filters.targetUserId}
              onChange={(event) =>
                setFilters({ ...filters, targetUserId: event.target.value })
              }
              placeholder="00000000-0000-0000-0000-000000000000"
            />
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
            Загружаем аудит
          </div>
        ) : data.content.length === 0 ? (
          <p className="text-sm text-muted-foreground">Записи не найдены.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[960px] text-sm">
              <thead>
                <tr className="border-b text-left text-xs uppercase tracking-wide text-muted-foreground">
                  <th className="pb-2 pr-3 font-medium">Время</th>
                  <th className="pb-2 pr-3 font-medium">Действие</th>
                  <th className="pb-2 pr-3 font-medium">Итог</th>
                  <th className="pb-2 pr-3 font-medium">Пользователь</th>
                  <th className="pb-2 pr-3 font-medium">Actor</th>
                  <th className="pb-2 font-medium">IP</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((entry) => (
                  <AuditRow key={entry.id} entry={entry} />
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="flex items-center justify-between gap-3">
          <span className="text-xs text-muted-foreground">
            Страница {data.totalPages === 0 ? 0 : page + 1} из {data.totalPages}
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
              disabled={page + 1 >= data.totalPages || loading}
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
  );
}

function AuditRow({ entry }: { entry: AuthAuditEntry }) {
  const outcomeClass =
    entry.outcome === "SUCCESS"
      ? "bg-emerald-100 text-emerald-800"
      : "bg-red-100 text-red-800";

  return (
    <tr className="border-b align-top last:border-b-0">
      <td className="py-2 pr-3 text-xs text-muted-foreground">{formatDate(entry.createdAt)}</td>
      <td className="py-2 pr-3">
        <div className="font-medium">{entry.action}</div>
        {entry.details ? (
          <div className="max-w-[280px] truncate text-xs text-muted-foreground" title={entry.details}>
            {entry.details}
          </div>
        ) : null}
      </td>
      <td className="py-2 pr-3">
        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${outcomeClass}`}>
          {entry.outcome}
        </span>
      </td>
      <td className="py-2 pr-3 font-mono text-xs">{entry.targetUserId ?? "—"}</td>
      <td className="py-2 pr-3 font-mono text-xs">{entry.actorId ?? "—"}</td>
      <td className="py-2 text-xs text-muted-foreground">{entry.ipAddress ?? "—"}</td>
    </tr>
  );
}

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("ru-RU", {
    dateStyle: "short",
    timeStyle: "medium",
  }).format(date);
}
