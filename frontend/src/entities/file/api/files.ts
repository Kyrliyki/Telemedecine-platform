import { refreshSession, authRequest } from "@/entities/session/api/session";
import { isAccessExpired, readSession } from "@/entities/session/model/storage";
import type { ChatFile, ChatFileListResponse } from "@/entities/file/model/types";
import { API_BASE_URL } from "@/shared/config/env";
import { ApiError } from "@/shared/api/http";

export function listChatFiles(chatId: string, limit = 100): Promise<ChatFileListResponse> {
  return authRequest<ChatFileListResponse>(
    `/api/v1/chats/${encodeURIComponent(chatId)}/files?limit=${limit}`,
  );
}

export function getChatFile(fileId: string): Promise<ChatFile> {
  return authRequest<ChatFile>(`/api/v1/files/${encodeURIComponent(fileId)}?download=true`);
}

export async function uploadChatFile(
  chatId: string,
  file: File,
  messageId?: string,
  onProgress?: (percent: number) => void,
): Promise<ChatFile> {
  let session = readSession();
  if (!session) {
    throw new ApiError(401, "Сессия истекла. Войдите снова.");
  }
  if (isAccessExpired(session)) {
    session = await refreshSession();
  }

  try {
    return await performUpload(session.accessToken, chatId, file, messageId, onProgress);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      const refreshed = await refreshSession(true);
      return performUpload(refreshed.accessToken, chatId, file, messageId, onProgress);
    }
    throw error;
  }
}

function performUpload(
  accessToken: string,
  chatId: string,
  file: File,
  messageId: string | undefined,
  onProgress?: (percent: number) => void,
): Promise<ChatFile> {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open(
      "POST",
      `${API_BASE_URL}/api/v1/chats/${encodeURIComponent(chatId)}/files`,
    );
    request.setRequestHeader("Authorization", `Bearer ${accessToken}`);
    request.setRequestHeader("Accept", "application/json");
    request.upload.onprogress = (event) => {
      if (event.lengthComputable && onProgress) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    };
    request.onload = () => {
      let payload: unknown;
      try {
        payload = JSON.parse(request.responseText);
      } catch {
        payload = undefined;
      }
      if (request.status >= 200 && request.status < 300) {
        resolve(payload as ChatFile);
        return;
      }
      const problem = payload as
        | { error?: { code?: string; message?: string }; detail?: string }
        | undefined;
      const message =
        problem?.error?.message ??
        (typeof problem?.detail === "string" ? problem.detail : undefined) ??
        `Не удалось загрузить файл (${request.status})`;
      reject(new ApiError(request.status, message, problem?.error?.code));
    };
    request.onerror = () => {
      reject(new ApiError(0, "Не удалось загрузить файл. Проверьте подключение."));
    };

    const form = new FormData();
    form.append("file", file, file.name);
    if (messageId) {
      form.append("message_id", messageId);
    }
    request.send(form);
  });
}
