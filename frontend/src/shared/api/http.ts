import { API_BASE_URL } from "@/shared/config/env";

export interface ProblemBody {
  status?: number;
  code?: string;
  title?: string;
  detail?: string;
  message?: string;
  errors?: Record<string, string>;
   
  error?: { code?: string; message?: string };
}

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly fieldErrors?: Record<string, string>;

  constructor(status: number, message: string, code?: string, fieldErrors?: Record<string, string>) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

interface RequestOptions {
  method?: "GET" | "POST" | "PATCH" | "PUT" | "DELETE";
  body?: unknown;
  accessToken?: string;
}

export async function apiRequest<T>(
  path: string,
  { method = "GET", body, accessToken }: RequestOptions = {},
): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, "Не удалось связаться с сервером. Проверьте подключение.");
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const payload = text ? safeJson(text) : undefined;

  if (!response.ok) {
    const problem = payload as ProblemBody | undefined;
    const detail = typeof problem?.detail === "string" ? problem.detail : undefined;
    const message =
      problem?.error?.message ??
      detail ??
      problem?.message ??
      problem?.title ??
      `Запрос завершился с ошибкой ${response.status}`;
    throw new ApiError(
      response.status,
      message,
      problem?.error?.code ?? problem?.code,
      problem?.errors,
    );
  }

  return payload as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

export function formatApiError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === "INVALID_CREDENTIALS") {
      return "Неверный email или пароль.";
    }
    if (error.code === "EMAIL_ALREADY_EXISTS" || error.code === "EMAIL_ALREADY_REGISTERED") {
      return "Аккаунт с таким email уже существует.";
    }
    if (error.code === "USER_BLOCKED") {
      return "Аккаунт заблокирован.";
    }
    if (error.code === "INVALID_STATUS_TRANSITION") {
      if (error.message.includes("PENDING_EMAIL_VERIFICATION")) {
        return "Подтвердите email по ссылке из письма, затем войдите снова.";
      }
      return "Действие недоступно в текущем статусе аккаунта.";
    }
    if (
      error.code === "TOKEN_EXPIRED" ||
      error.code === "INVALID_TOKEN" ||
      error.code === "UNAUTHORIZED"
    ) {
      return "Сессия истекла. Войдите снова.";
    }
    if (error.code === "INVALID_VERIFICATION_TOKEN") {
      return "Ссылка подтверждения недействительна или истекла.";
    }
    if (error.code === "OAUTH_ALREADY_LINKED") {
      return "Этот аккаунт уже привязан к выбранному провайдеру.";
    }
    if (error.code === "ROLE_ASSIGNMENT_NOT_ALLOWED") {
      return "Недостаточно прав, чтобы выдать эту роль.";
    }
    if (error.code === "VALIDATION_ERROR" && error.fieldErrors) {
      const first = Object.values(error.fieldErrors)[0];
      if (first) {
        return first;
      }
    }
    return error.message;
  }
  return "Произошла непредвиденная ошибка. Попробуйте ещё раз.";
}
