import { authRequest } from "@/entities/session/api/session";
import { readSession } from "@/entities/session/model/storage";
import type {
  ChatDialog,
  ChatParticipant,
  DialogWithHistory,
} from "@/entities/chat/model/types";

interface CreateDialogPayload {
  request_id: string;
  participants: ChatParticipant[];
  request_status?: "open" | "closed";
}

export function getDialogByRequest(requestId: string): Promise<DialogWithHistory> {
  return authRequest<DialogWithHistory>(
    `/api/v1/chat/by-request/${encodeURIComponent(requestId)}?limit=100`,
  );
}

export function createDialog(payload: CreateDialogPayload): Promise<ChatDialog> {
  return authRequest<ChatDialog>("/api/v1/chat", {
    method: "POST",
    body: payload,
  });
}

export function chatSocketUrl(token: string): string {
  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  return `${protocol}://${window.location.host}/ws/chat?token=${encodeURIComponent(token)}`;
}

export async function sendChatNotice(
  appointmentId: string,
  content: string,
): Promise<void> {
  const history = await getDialogByRequest(appointmentId);
  const session = readSession();
  if (!session) {
    throw new Error("Сессия истекла");
  }

  await new Promise<void>((resolve, reject) => {
    const socket = new WebSocket(chatSocketUrl(session.accessToken));
    const clientMsgId = crypto.randomUUID();
    const timeout = window.setTimeout(() => {
      socket.close();
      reject(new Error("Не удалось отправить сообщение в чат"));
    }, 5000);

    function cleanup() {
      window.clearTimeout(timeout);
      socket.close();
    }

    socket.onopen = () => {
      socket.send(
        JSON.stringify({
          type: "message.send",
          payload: {
            dialog_id: history.id,
            content,
            client_msg_id: clientMsgId,
          },
        }),
      );
    };
    socket.onmessage = (event) => {
      try {
        const frame = JSON.parse(event.data as string) as {
          type?: string;
          payload?: { client_msg_id?: string };
        };
        if (frame.type === "message.ack" && frame.payload?.client_msg_id === clientMsgId) {
          cleanup();
          resolve();
        }
      } catch {
        
      }
    };
    socket.onerror = () => {
      cleanup();
      reject(new Error("Не удалось отправить сообщение в чат"));
    };
  });
}
