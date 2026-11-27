import type { DoctorProfile, UserProfile } from "@/entities/user/model/types";

export const roleLabels: Record<string, string> = {
  ADMIN: "Администратор",
  DOCTOR: "Врач",
  PATIENT: "Пациент",
};

export const accountStatusLabels: Record<string, string> = {
  ACTIVE: "Активен",
  BLOCKED: "Заблокирован",
  PENDING_EMAIL_VERIFICATION: "Ждёт подтверждения email",
  PENDING_INVITATION: "Ждёт принятия приглашения",
};

export const verificationLabels: Record<string, string> = {
  PENDING: "На проверке",
  VERIFIED: "Проверен",
  REJECTED: "Отклонён",
};

export function personName(
  person: Pick<UserProfile | DoctorProfile, "firstName" | "lastName"> & { email: string },
): string {
  const name = [person.firstName, person.lastName].filter(Boolean).join(" ").trim();
  return name || person.email;
}

export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("ru-RU", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

export function formatExperience(years: number): string {
  const mod10 = years % 10;
  const mod100 = years % 100;
  if (mod10 === 1 && mod100 !== 11) {
    return `${years} год`;
  }
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
    return `${years} года`;
  }
  return `${years} лет`;
}
