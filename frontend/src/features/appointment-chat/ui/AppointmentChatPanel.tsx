import { AlertCircle, Loader2, Paperclip, Phone, Upload, X } from "lucide-react";
import {
  FormEvent,
  forwardRef,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
} from "react";
import { createPortal } from "react-dom";

import { chatSocketUrl, createDialog, getDialogByRequest } from "@/entities/chat/api/chat";
import type { ChatDialog, ChatMessage } from "@/entities/chat/model/types";
import { listChatFiles, uploadChatFile } from "@/entities/file/api/files";
import type { ChatFile } from "@/entities/file/model/types";
import type { Appointment } from "@/entities/appointment/model/types";
import { readSession } from "@/entities/session/model/storage";
import { createCall, joinCall } from "@/entities/video/api/video";
import type { JoinCallResult } from "@/entities/video/model/types";
import { ApiError, formatApiError } from "@/shared/api/http";
import { cn } from "@/shared/lib/cn";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";

interface ChatMessageItem extends ChatMessage {
  client_msg_id?: string;
  pending?: boolean;
}

const MAX_FILE_SIZE = 50 * 1024 * 1024;

const FILE_MESSAGE_MARKER = "\u200b";

interface AppointmentChatPanelProps {
  open: boolean;
  appointment: Appointment | null;
  currentUserId: string;
  canStartCall: boolean;
  resolveName: (userId: string) => string;
  onCallStarted: (join: JoinCallResult) => void;
  onClose: () => void;
}

export interface AppointmentChatPanelHandle {
  sendNotice: (content: string) => void;
}

interface SocketFrame {
  type: string;
  payload?: Record<string, unknown>;
}

export const AppointmentChatPanel = forwardRef<
  AppointmentChatPanelHandle,
  AppointmentChatPanelProps
>(function AppointmentChatPanel(
  {
    open,
    appointment,
    currentUserId,
    canStartCall,
    resolveName,
    onCallStarted,
    onClose,
  },
  ref,
) {
  const [dialog, setDialog] = useState<ChatDialog | null>(null);
  const [messages, setMessages] = useState<ChatMessageItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [input, setInput] = useState("");
  const [connected, setConnected] = useState(false);
  const [callBusy, setCallBusy] = useState(false);
  const [filesByMessage, setFilesByMessage] = useState<Record<string, ChatFile[]>>({});
  const [dragging, setDragging] = useState(false);
  const [attaching, setAttaching] = useState(false);
  const [uploadName, setUploadName] = useState<string | null>(null);
  const [uploadPercent, setUploadPercent] = useState<number | null>(null);

  const socketRef = useRef<WebSocket | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const ackResolversRef = useRef<Map<string, (messageId: string) => void>>(new Map());

  useEffect(() => {
    if (!open) {
      return;
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        onClose();
      }
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [onClose, open]);

  useEffect(() => {
    if (!open || !appointment) {
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    setDialog(null);
    setMessages([]);
    setFilesByMessage({});
    setDragging(false);
    setAttaching(false);
    setUploadName(null);
    setUploadPercent(null);
    setInput("");

    async function load() {
      try {
        let history;
        try {
          history = await getDialogByRequest(appointment!.id);
        } catch (requestError) {
          if (requestError instanceof ApiError && requestError.status === 404) {
            await createDialog({
              request_id: appointment!.id,
              participants: [
                { user_id: appointment!.patient_id, role: "client" },
                { user_id: appointment!.doctor_id, role: "operator" },
              ],
              request_status: "open",
            });
            history = await getDialogByRequest(appointment!.id);
          } else {
            throw requestError;
          }
        }
        if (!cancelled) {
          setDialog(history);
          setMessages(sortMessages(history.messages.items ?? []));
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
  }, [appointment, open]);

  useEffect(() => {
    if (!open || !dialog) {
      return;
    }
    let cancelled = false;
    listChatFiles(dialog.id)
      .then((page) => {
        if (!cancelled) {
          setFilesByMessage(groupFiles(page.items ?? []));
        }
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [dialog, open]);

  useEffect(() => {
    if (!open || !dialog || messages.length === 0) {
      return;
    }
    const last = messages[messages.length - 1];
    if (last.content !== FILE_MESSAGE_MARKER) {
      return;
    }

    let cancelled = false;
    const timers = [800, 2200, 4500, 8000].map((delay) =>
      window.setTimeout(() => {
        listChatFiles(dialog.id)
          .then((page) => {
            if (!cancelled) {
              setFilesByMessage((previous) =>
                mergeFileGroups(previous, groupFiles(page.items ?? [])),
              );
            }
          })
          .catch(() => undefined);
      }, delay),
    );

    return () => {
      cancelled = true;
      timers.forEach((timer) => window.clearTimeout(timer));
    };
  }, [dialog, messages, open]);

  useEffect(() => {
    if (!open || !dialog) {
      return;
    }
    const session = readSession();
    if (!session) {
      setError("Сессия истекла. Войдите снова.");
      return;
    }

    const socket = new WebSocket(chatSocketUrl(session.accessToken));
    socketRef.current = socket;

    socket.onopen = () => setConnected(true);
    socket.onclose = () => setConnected(false);
    socket.onmessage = (event) => {
      let frame: SocketFrame;
      try {
        frame = JSON.parse(event.data as string) as SocketFrame;
      } catch {
        return;
      }

      if (frame.type === "message.new" && frame.payload) {
        const payload = frame.payload as {
          id: string;
          dialog_id: string;
          author_id: string;
          content: string;
          created_at: string;
          client_msg_id?: string;
        };
        if (payload.dialog_id !== dialog.id) {
          return;
        }
        setMessages((previous) => {
          if (previous.some((item) => item.id === payload.id)) {
            return previous;
          }
          return sortMessages([
            ...previous,
            {
              id: payload.id,
              dialog_id: payload.dialog_id,
              author_id: payload.author_id,
              content: payload.content,
              created_at: payload.created_at,
              client_msg_id: payload.client_msg_id,
            },
          ]);
        });
        return;
      }

      if (frame.type === "message.ack" && frame.payload) {
        const payload = frame.payload as {
          id: string;
          client_msg_id: string;
          created_at: string;
        };
        setMessages((previous) =>
          sortMessages(
            previous.map((item) =>
              item.client_msg_id === payload.client_msg_id
                ? { ...item, id: payload.id, created_at: payload.created_at, pending: false }
                : item,
            ),
          ),
        );
        const resolver = ackResolversRef.current.get(payload.client_msg_id);
        if (resolver) {
          ackResolversRef.current.delete(payload.client_msg_id);
          resolver(payload.id);
        }
        return;
      }

      if (frame.type === "error" && frame.payload) {
        const payload = frame.payload as { message?: string; code?: string };
        setError(payload.message ?? payload.code ?? "Ошибка чата");
      }
    };

    return () => {
      socket.close();
      socketRef.current = null;
      setConnected(false);
    };
  }, [dialog, open]);

  useEffect(() => {
    const container = scrollRef.current;
    if (container) {
      container.scrollTop = container.scrollHeight;
    }
  }, [messages.length]);

  async function startVideoCall() {
    if (!appointment) {
      return;
    }
    setCallBusy(true);
    setError(null);
    try {
      const call = await createCall({
        appointment_id: appointment.id,
        patient_id: appointment.patient_id,
        doctor_id: appointment.doctor_id,
      });
      const joined = await joinCall(call.id, resolveName(currentUserId));
      sendContent("Видеозвонок начат");
      onCallStarted(joined);
    } catch (requestError) {
      setError(formatApiError(requestError));
    } finally {
      setCallBusy(false);
    }
  }

  function sendContent(
    rawContent: string,
    onAck?: (messageId: string) => void,
  ): string | null {
    const content = rawContent.trim();
    if (!content || !dialog) {
      return null;
    }
    const socket = socketRef.current;
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      setError("Нет соединения с чатом. Попробуйте ещё раз.");
      return null;
    }

    const clientMsgId = crypto.randomUUID();
    setError(null);
    setMessages((previous) =>
      sortMessages([
        ...previous,
        {
          id: `pending-${clientMsgId}`,
          dialog_id: dialog.id,
          author_id: currentUserId,
          content,
          created_at: new Date().toISOString(),
          client_msg_id: clientMsgId,
          pending: true,
        },
      ]),
    );
    if (onAck) {
      ackResolversRef.current.set(clientMsgId, onAck);
    }
    socket.send(
      JSON.stringify({
        type: "message.send",
        payload: {
          dialog_id: dialog.id,
          content,
          client_msg_id: clientMsgId,
        },
      }),
    );
    return clientMsgId;
  }

  function sendContentWithAck(content: string): Promise<string> {
    return new Promise<string>((resolve, reject) => {
      let clientMsgId: string | null = null;
      const timer = window.setTimeout(() => {
        if (clientMsgId) {
          ackResolversRef.current.delete(clientMsgId);
        }
        reject(new Error("Сервер не подтвердил отправку сообщения."));
      }, 5000);

      clientMsgId = sendContent(content, (messageId) => {
        window.clearTimeout(timer);
        resolve(messageId);
      });
      if (!clientMsgId) {
        window.clearTimeout(timer);
        reject(new Error("Нет соединения с чатом."));
      }
    });
  }

  function sendMessage(event: FormEvent) {
    event.preventDefault();
    if (sendContent(input)) {
      setInput("");
    }
  }

  async function handleFiles(fileList: FileList | File[]) {
    const files = Array.from(fileList);
    if (!files.length || !dialog || !appointment) {
      return;
    }
    if (
      dialog.request_status !== "open" ||
      (appointment.status !== "requested" && appointment.status !== "confirmed")
    ) {
      setError("Чат закрыт: приём отменён или завершён.");
      return;
    }
    const tooLarge = files.find((file) => file.size > MAX_FILE_SIZE);
    if (tooLarge) {
      setError(`Файл «${tooLarge.name}» больше ${formatFileSize(MAX_FILE_SIZE)}.`);
      return;
    }

    setAttaching(true);
    setError(null);
    try {
      for (const file of files) {
        setUploadName(file.name);
        setUploadPercent(0);
        const messageId = await sendContentWithAck(FILE_MESSAGE_MARKER);
        const uploaded = await uploadChatFile(
          dialog.id,
          file,
          messageId,
          setUploadPercent,
        );
        setFilesByMessage((previous) => ({
          ...previous,
          [messageId]: [...(previous[messageId] ?? []), uploaded],
        }));
      }
    } catch (uploadError) {
      setError(formatApiError(uploadError));
    } finally {
      setAttaching(false);
      setUploadName(null);
      setUploadPercent(null);
    }
  }

  useImperativeHandle(ref, () => ({
    sendNotice: (content: string) => {
      sendContent(content);
    },
  }));

  if (!open || !appointment) {
    return null;
  }

  const isClosed = Boolean(
    dialog &&
      (dialog.request_status !== "open" ||
        (appointment.status !== "requested" && appointment.status !== "confirmed")),
  );

  const startsAt = new Date(appointment.starts_at);
  const isDoctorView = appointment.doctor_id === currentUserId;
  const counterpartId = isDoctorView ? appointment.patient_id : appointment.doctor_id;
  const subtitle = `${formatChatDate(startsAt)} · ${
    isDoctorView ? "пациент" : "врач"
  } ${resolveName(counterpartId)}`;

  return createPortal(
    <div className="fixed inset-0 z-50">
      <div
        aria-hidden="true"
        className="absolute inset-0 bg-foreground/30 backdrop-blur-[1px]"
        onClick={onClose}
      />
      <aside
        role="dialog"
        aria-modal="true"
        aria-label="Чат по приёму"
        className="absolute inset-y-0 right-0 flex w-full max-w-md flex-col overflow-hidden border-l bg-card text-card-foreground shadow-xl"
        onDragOver={(event) => {
          event.preventDefault();
          if (!isClosed) {
            setDragging(true);
          }
        }}
        onDragLeave={(event) => {
          const next = event.relatedTarget as Node | null;
          if (!next || !event.currentTarget.contains(next)) {
            setDragging(false);
          }
        }}
        onDrop={(event) => {
          event.preventDefault();
          setDragging(false);
          void handleFiles(event.dataTransfer.files);
        }}
      >
        <input
          ref={fileInputRef}
          type="file"
          multiple
          className="hidden"
          onChange={(event) => {
            if (event.target.files?.length) {
              void handleFiles(event.target.files);
            }
            event.target.value = "";
          }}
        />
        {dragging ? (
          <div className="pointer-events-none absolute inset-0 z-20 flex items-center justify-center border-2 border-dashed border-primary bg-primary/10 p-6 text-center">
            <div className="space-y-2 rounded-xl bg-card px-5 py-4 shadow-lg">
              <Upload className="mx-auto h-7 w-7 text-primary" />
              <p className="font-medium">Отпустите файл, чтобы отправить</p>
              <p className="text-xs text-muted-foreground">До 50 МБ, можно несколько</p>
            </div>
          </div>
        ) : null}
        <header className="flex items-start justify-between gap-3 border-b px-5 py-4">
          <div className="space-y-1">
            <h2 className="text-lg font-semibold">Чат по приёму</h2>
            <p className="text-sm text-muted-foreground">{subtitle}</p>
          </div>
          <div className="flex items-center gap-2">
            {canStartCall ? (
              <Button
                type="button"
                size="sm"
                className="rounded-lg"
                disabled={callBusy || appointment.status !== "confirmed"}
                title={
                  appointment.status === "confirmed"
                    ? "Начать видеозвонок"
                    : "Сначала подтвердите приём"
                }
                onClick={() => void startVideoCall()}
              >
                {callBusy ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Phone className="h-4 w-4" />
                )}
                Позвонить
              </Button>
            ) : null}
            <Button
              type="button"
              variant="outline"
              size="icon"
              className="rounded-lg"
              onClick={onClose}
              aria-label="Закрыть чат"
            >
              <X className="h-4 w-4" />
            </Button>
          </div>
        </header>

        <div
          ref={scrollRef}
          className="flex-1 space-y-4 overflow-y-auto bg-secondary/40 px-5 py-5"
        >
          {loading ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" />
              Загружаем сообщения
            </div>
          ) : messages.length === 0 ? (
            <p className="py-10 text-center text-sm text-muted-foreground">
              Сообщений пока нет. Напишите первым.
            </p>
          ) : (
            messages.map((message) => {
              const own = message.author_id === currentUserId;
              const attachedFiles = filesByMessage[message.id] ?? [];
              const hideText =
                message.content === FILE_MESSAGE_MARKER ||
                (attachedFiles.length > 0 && message.content.startsWith("📎 "));
              return (
                <div
                  key={message.id}
                  className={cn("flex flex-col", own ? "items-end" : "items-start")}
                >
                  <span className="mb-1 text-xs text-muted-foreground">
                    {own ? "Вы" : resolveName(message.author_id)} ·{" "}
                    {formatChatTime(new Date(message.created_at))}
                  </span>
                  {!hideText ? (
                    <div
                      className={cn(
                        "max-w-[85%] whitespace-pre-wrap break-words rounded-2xl px-4 py-2.5 text-sm",
                        own
                          ? "rounded-tr-md bg-primary text-primary-foreground"
                          : "rounded-tl-md border bg-card",
                        message.pending ? "opacity-70" : null,
                      )}
                    >
                      {message.content}
                    </div>
                  ) : null}
                  {attachedFiles.length > 0 ? (
                    <div className="mt-1 flex w-full max-w-[85%] flex-col gap-1">
                      {attachedFiles.map((file) => (
                        <a
                          key={file.id}
                          href={file.download_url ?? "#"}
                          target="_blank"
                          rel="noreferrer"
                          onClick={(event) => {
                            if (!file.download_url) {
                              event.preventDefault();
                            }
                          }}
                          className="flex items-center gap-2 rounded-xl border bg-card px-3 py-2 text-xs hover:bg-accent"
                        >
                          <Paperclip className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                          <span className="truncate">{file.file_name}</span>
                          <span className="ml-auto shrink-0 text-muted-foreground">
                            {formatFileSize(file.size_bytes)}
                          </span>
                        </a>
                      ))}
                    </div>
                  ) : null}
                </div>
              );
            })
          )}
        </div>

        <footer className="space-y-2 border-t p-4">
          {error ? (
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}

          {attaching ? (
            <div className="space-y-1">
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                Загружаем {uploadName ?? "файл"}
                {uploadPercent !== null ? ` · ${uploadPercent}%` : ""}
              </div>
              <div className="h-1.5 w-full overflow-hidden rounded-full bg-secondary">
                <div
                  className="h-full bg-primary transition-all"
                  style={{ width: `${uploadPercent ?? 0}%` }}
                />
              </div>
            </div>
          ) : null}

          {loading || !dialog ? (
            <p className="text-center text-sm text-muted-foreground">Подготавливаем чат…</p>
          ) : isClosed ? (
            <p className="text-center text-sm text-muted-foreground">
              Чат закрыт: приём отменён или завершён.
            </p>
          ) : (
            <form className="flex items-center gap-2" onSubmit={sendMessage}>
              <Button
                type="button"
                variant="outline"
                size="icon"
                className="h-11 w-11 shrink-0 rounded-full"
                disabled={attaching}
                title="Прикрепить файл"
                aria-label="Прикрепить файл"
                onClick={() => fileInputRef.current?.click()}
              >
                <Paperclip className="h-4 w-4" />
              </Button>
              <Input
                value={input}
                onChange={(event) => setInput(event.target.value)}
                placeholder="Сообщение..."
                className="h-11 rounded-full border-primary/40 focus-visible:ring-primary/40"
                maxLength={4096}
              />
              <Button
                type="submit"
                className="h-11 shrink-0 rounded-full px-5"
                disabled={!input.trim() || !connected}
              >
                Отправить
              </Button>
            </form>
          )}
          {!connected && !isClosed && !error ? (
            <p className="text-center text-xs text-muted-foreground">Соединяемся с чатом…</p>
          ) : null}
        </footer>
      </aside>
    </div>,
    document.body,
  );
});

function groupFiles(files: ChatFile[]): Record<string, ChatFile[]> {
  const grouped: Record<string, ChatFile[]> = {};
  files.forEach((file) => {
    if (!file.message_id) {
      return;
    }
    (grouped[file.message_id] ??= []).push(file);
  });
  Object.values(grouped).forEach((items) => items.sort(sortFiles));
  return grouped;
}

function mergeFileGroups(
  current: Record<string, ChatFile[]>,
  incoming: Record<string, ChatFile[]>,
): Record<string, ChatFile[]> {
  const merged = { ...current };
  Object.entries(incoming).forEach(([messageId, files]) => {
    const existing = merged[messageId] ?? [];
    const known = new Set(existing.map((file) => file.id));
    merged[messageId] = [...existing, ...files.filter((file) => !known.has(file.id))]
      .sort(sortFiles);
  });
  return merged;
}

function sortFiles(left: ChatFile, right: ChatFile): number {
  return Date.parse(left.created_at) - Date.parse(right.created_at);
}

function sortMessages(items: ChatMessageItem[]): ChatMessageItem[] {
  return [...items].sort((left, right) => {
    const delta = Date.parse(left.created_at) - Date.parse(right.created_at);
    return delta !== 0 ? delta : left.id.localeCompare(right.id);
  });
}

function formatChatDate(date: Date): string {
  return new Intl.DateTimeFormat("ru-RU", {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} Б`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} КБ`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} МБ`;
}

function formatChatTime(date: Date): string {
  return new Intl.DateTimeFormat("ru-RU", {
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}
