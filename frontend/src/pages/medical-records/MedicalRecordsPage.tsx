import {
  AlertCircle,
  BookOpen,
  ClipboardList,
  Loader2,
  RefreshCw,
  UsersRound,
} from "lucide-react";
import { useEffect, useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";

import {
  getMedicalRecord,
  listMyMedicalRecords,
  type MedicalRecordListQuery,
} from "@/entities/emr/api/emr";
import type { MedicalRecord } from "@/entities/emr/model/types";
import { fetchCurrentUser } from "@/entities/session/api/current-user";
import { isDualRole, useRoleMode } from "@/entities/session/model/roleMode";
import { readSession } from "@/entities/session/model/storage";
import type { CurrentUser } from "@/entities/session/model/types";
import { fetchUserProfile, listDoctors } from "@/entities/user/api/userApi";
import { personName } from "@/entities/user/model/display";
import { MedicalRecordCard } from "@/features/medical-records/ui/MedicalRecordCard";
import { ApiError, formatApiError } from "@/shared/api/http";
import { Alert, AlertDescription, AlertTitle } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card";
import { RoleModeSwitch } from "@/shared/ui/role-mode-switch";
import { cn } from "@/shared/lib/cn";
import { AppHeader } from "@/widgets/app-header/ui/AppHeader";

const QUERY: MedicalRecordListQuery = { page: 0, size: 100 };

export function MedicalRecordsPage() {
  const navigate = useNavigate();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [roleMode, setRoleMode] = useRoleMode(user?.roles);

  useEffect(() => {
    document.title = "Telemed — медкнижки";
  }, []);

  useEffect(() => {
    if (!readSession()) {
      return;
    }
    let cancelled = false;
    setLoading(true);
    fetchCurrentUser()
      .then((profile) => {
        if (!cancelled) {
          setUser(profile);
        }
      })
      .catch((requestError) => {
        if (requestError instanceof ApiError && requestError.status === 401) {
          navigate("/login", { replace: true });
          return;
        }
        if (!cancelled) {
          setError(formatApiError(requestError));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [navigate]);

  if (!readSession()) {
    return <Navigate to="/login" replace />;
  }

  const hasDoctorRole = user?.roles.includes("DOCTOR") ?? false;
  const isAdmin = user?.roles.includes("ADMIN") ?? false;
  const dual = isDualRole(user?.roles) && !isAdmin;
  const isDoctor = hasDoctorRole && (!dual || roleMode === "doctor");

  return (
    <div className="min-h-screen bg-background">
      <AppHeader />
      <main className="mx-auto max-w-6xl space-y-6 px-6 py-10">
        <div className="space-y-1">
          {dual ? (
            <RoleModeSwitch
              mode={roleMode}
              onChange={setRoleMode}
              className="mb-2"
            />
          ) : null}
          <h1 className="text-2xl font-semibold">
            {isDoctor ? "Медкнижки пациентов" : "Моя медкнижка"}
          </h1>
          <p className="text-sm text-muted-foreground">
            {isDoctor
              ? "Пациенты, которые обращались к вам, и их медицинские записи."
              : "Диагнозы, назначения и показатели из ваших приёмов."}
          </p>
        </div>

        {loading ? (
          <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" />
            Загружаем данные
          </div>
        ) : error ? (
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertTitle>Не удалось загрузить медкнижку</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : user ? (
          isDoctor ? (
            <DoctorRecordsView />
          ) : (
            <PatientRecordsView
              view={user.roles.includes("PATIENT") ? "patient" : undefined}
            />
          )
        ) : null}
      </main>
    </div>
  );
}

function PatientRecordsView({ view }: { view?: "patient" }) {
  const [records, setRecords] = useState<MedicalRecord[]>([]);
  const [doctorNames, setDoctorNames] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    Promise.all([
      listMyMedicalRecords({ ...QUERY, view }),
      listDoctors({ page: 0, size: 100 }).catch(() => null),
    ])
      .then(async ([page, doctors]) => {
        if (cancelled) {
          return;
        }
        const items = [...(page.content ?? [])].sort(
          (left, right) => Date.parse(right.createdAt) - Date.parse(left.createdAt),
        );
        setRecords(await enrichRecords(items));
        const names: Record<string, string> = {};
        doctors?.content?.forEach((doctor) => {
          names[doctor.userId] = personName(doctor);
        });
        setDoctorNames(names);
      })
      .catch((requestError) => {
        if (!cancelled) {
          setError(formatApiError(requestError));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button
          variant="outline"
          size="sm"
          onClick={() => setReloadKey((value) => value + 1)}
          disabled={loading}
        >
          <RefreshCw className={loading ? "h-4 w-4 animate-spin" : "h-4 w-4"} />
          Обновить
        </Button>
      </div>

      {error ? (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : null}

      {loading ? (
        <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          Загружаем записи
        </div>
      ) : records.length === 0 ? (
        <EmptyState
          title="Медкнижка пока пуста"
          description="Записи появятся здесь после завершённых приёмов и заполнения врачом."
        />
      ) : (
        <div className="space-y-4">
          {records.map((record) => (
            <MedicalRecordCard
              key={record.id}
              record={record}
              doctorName={doctorNames[record.doctorId]}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function DoctorRecordsView() {
  const [records, setRecords] = useState<MedicalRecord[]>([]);
  const [patientNames, setPatientNames] = useState<Record<string, string>>({});
  const [selectedPatientId, setSelectedPatientId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    listMyMedicalRecords({ ...QUERY, view: "doctor" })
      .then(async (page) => {
        if (cancelled) {
          return;
        }
        const items = await enrichRecords(
          [...(page.content ?? [])].sort(
            (left, right) => Date.parse(right.createdAt) - Date.parse(left.createdAt),
          ),
        );
        setRecords(items);

        const ids = Array.from(new Set(items.map((record) => record.patientId))).slice(0, 100);
        const profiles = await Promise.allSettled(ids.map((id) => fetchUserProfile(id)));
        if (cancelled) {
          return;
        }
        const names: Record<string, string> = {};
        profiles.forEach((result, index) => {
          if (result.status === "fulfilled") {
            names[ids[index]] = personName(result.value);
          }
        });
        setPatientNames(names);
        setSelectedPatientId((current) => current ?? ids[0] ?? null);
      })
      .catch((requestError) => {
        if (!cancelled) {
          setError(formatApiError(requestError));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  const groups = buildPatientGroups(records);
  const selectedGroup = groups.find((group) => group.patientId === selectedPatientId) ?? groups[0];

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button
          variant="outline"
          size="sm"
          onClick={() => setReloadKey((value) => value + 1)}
          disabled={loading}
        >
          <RefreshCw className={loading ? "h-4 w-4 animate-spin" : "h-4 w-4"} />
          Обновить
        </Button>
      </div>

      {error ? (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : null}

      {loading ? (
        <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          Загружаем медкнижки
        </div>
      ) : groups.length === 0 ? (
        <EmptyState
          title="Пока нет медкнижек"
          description="Медкнижки появятся после завершённых приёмов, по которым вы заполнили медицинскую запись."
        />
      ) : (
        <div className="grid gap-6 lg:grid-cols-[280px_1fr]">
          <Card className="h-fit">
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-base">
                <UsersRound className="h-4 w-4 text-primary" />
                Пациенты
              </CardTitle>
            </CardHeader>
            <CardContent className="max-h-[70vh] space-y-1 overflow-y-auto p-2">
              {groups.map((group) => {
                const active = selectedGroup?.patientId === group.patientId;
                return (
                  <button
                    key={group.patientId}
                    type="button"
                    onClick={() => setSelectedPatientId(group.patientId)}
                    className={cn(
                      "w-full rounded-lg px-3 py-2 text-left transition-colors",
                      active ? "bg-secondary" : "hover:bg-accent",
                    )}
                  >
                    <p className="truncate text-sm font-medium">
                      {patientNames[group.patientId] ?? group.patientId.slice(0, 8)}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {formatRecordsCount(group.records.length)} ·{" "}
                      {formatShortDate(group.records[0].createdAt)}
                    </p>
                  </button>
                );
              })}
            </CardContent>
          </Card>

          <div className="space-y-3">
            {selectedGroup ? (
              <>
                <div className="space-y-1">
                  <h2 className="flex items-center gap-2 text-lg font-semibold">
                    <ClipboardList className="h-5 w-5 text-primary" />
                    {patientNames[selectedGroup.patientId] ??
                      selectedGroup.patientId.slice(0, 8)}
                  </h2>
                  <p className="text-sm text-muted-foreground">
                    Медицинские записи, оформленные вами.
                  </p>
                </div>
                {selectedGroup.records.map((record) => (
                  <MedicalRecordCard key={record.id} record={record} />
                ))}
              </>
            ) : null}
          </div>
        </div>
      )}
    </div>
  );
}

interface PatientGroup {
  patientId: string;
  records: MedicalRecord[];
}

function buildPatientGroups(records: MedicalRecord[]): PatientGroup[] {
  const map = new Map<string, MedicalRecord[]>();
  records.forEach((record) => {
    const list = map.get(record.patientId) ?? [];
    list.push(record);
    map.set(record.patientId, list);
  });
  return Array.from(map.entries())
    .map(([patientId, items]) => ({
      patientId,
      records: items.sort(
        (left, right) => Date.parse(right.createdAt) - Date.parse(left.createdAt),
      ),
    }))
    .sort(
      (left, right) =>
        Date.parse(right.records[0].createdAt) - Date.parse(left.records[0].createdAt),
    );
}

function EmptyState({ title, description }: { title: string; description: string }) {
  return (
    <Card className="border-dashed">
      <CardContent className="flex flex-col items-center gap-3 py-14 text-center">
        <span className="flex h-12 w-12 items-center justify-center rounded-full bg-accent text-primary">
          <BookOpen className="h-6 w-6" />
        </span>
        <h2 className="text-lg font-semibold">{title}</h2>
        <p className="max-w-md text-sm text-muted-foreground">{description}</p>
      </CardContent>
    </Card>
  );
}

async function enrichRecords(records: MedicalRecord[]): Promise<MedicalRecord[]> {
  const detailed = await Promise.allSettled(
    records.map((record) => getMedicalRecord(record.id)),
  );
  return records.map((record, index) => {
    const result = detailed[index];
    return result.status === "fulfilled" ? result.value : record;
  });
}

function formatRecordsCount(count: number): string {
  const mod10 = count % 10;
  const mod100 = count % 100;
  if (mod10 === 1 && mod100 !== 11) {
    return `${count} запись`;
  }
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
    return `${count} записи`;
  }
  return `${count} записей`;
}

function formatShortDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("ru-RU", {
    day: "numeric",
    month: "short",
  }).format(date);
}
