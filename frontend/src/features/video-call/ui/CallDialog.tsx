import {
  Room,
  RoomEvent,
  Track,
  type LocalTrackPublication,
  type RemoteParticipant,
  type RemoteTrack,
  type RemoteTrackPublication,
} from "livekit-client";
import {
  Maximize2,
  Mic,
  MicOff,
  Minimize2,
  PhoneOff,
  Video,
  VideoOff,
  X,
} from "lucide-react";
import {
  useEffect,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
} from "react";
import { createPortal } from "react-dom";

import { endCall } from "@/entities/video/api/video";
import type { JoinCallResult } from "@/entities/video/model/types";
import { formatApiError } from "@/shared/api/http";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { cn } from "@/shared/lib/cn";

interface CallDialogProps {
  open: boolean;
  join: JoinCallResult | null;
  counterpartName: string;
  canEnd: boolean;
  onClose: () => void;
  onEnded?: () => void;
}

export function CallDialog({
  open,
  join,
  counterpartName,
  canEnd,
  onClose,
  onEnded,
}: CallDialogProps) {
  const [connected, setConnected] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [micOn, setMicOn] = useState(true);
  const [cameraOn, setCameraOn] = useState(true);
  const [remotePresent, setRemotePresent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [mediaNote, setMediaNote] = useState<string | null>(null);
  const [seconds, setSeconds] = useState(0);
  const [ending, setEnding] = useState(false);
  const [minimized, setMinimized] = useState(false);
  const [position, setPosition] = useState({ x: 16, y: 16 });

  const roomRef = useRef<Room | null>(null);
  const localVideoRef = useRef<HTMLVideoElement | null>(null);
  const remoteVideoRef = useRef<HTMLVideoElement | null>(null);
  const audioContainerRef = useRef<HTMLDivElement | null>(null);
  const miniRef = useRef<HTMLDivElement | null>(null);
  const dragOffset = useRef({ x: 0, y: 0 });

  useEffect(() => {
    if (!open || !join) {
      return;
    }
    let cancelled = false;
    setConnected(false);
    setConnecting(true);
    setError(null);
    setMediaNote(null);
    setRemotePresent(false);
    setSeconds(0);
    setMicOn(true);
    setCameraOn(true);
    setMinimized(false);
    setPosition({ x: 16, y: 16 });

    const room = new Room({ adaptiveStream: true, dynacast: true });
    roomRef.current = room;

    function onTrackSubscribed(
      track: RemoteTrack,
      _publication: RemoteTrackPublication,
      _participant: RemoteParticipant,
    ) {
      if (track.kind === Track.Kind.Video && remoteVideoRef.current) {
        track.attach(remoteVideoRef.current);
        setRemotePresent(true);
      }
      if (track.kind === Track.Kind.Audio && audioContainerRef.current) {
        const element = track.attach();
        element.autoplay = true;
        audioContainerRef.current.appendChild(element);
      }
    }

    function onTrackUnsubscribed(track: RemoteTrack) {
      track.detach().forEach((element) => element?.remove());
      if (track.kind === Track.Kind.Video) {
        setRemotePresent(false);
      }
    }

    function onParticipantConnected() {
      setRemotePresent(room.remoteParticipants.size > 0);
    }

    function onParticipantDisconnected() {
      setRemotePresent(room.remoteParticipants.size > 0);
    }

    function onDisconnected() {
      setConnected(false);
    }

    room.on(RoomEvent.TrackSubscribed, onTrackSubscribed);
    room.on(RoomEvent.TrackUnsubscribed, onTrackUnsubscribed);
    room.on(RoomEvent.ParticipantConnected, onParticipantConnected);
    room.on(RoomEvent.ParticipantDisconnected, onParticipantDisconnected);
    room.on(RoomEvent.Disconnected, onDisconnected);

    async function connect() {
      try {
        await room.connect(join!.livekit_url, join!.token);
        if (cancelled) {
          await room.disconnect();
          return;
        }
        setConnected(true);
        setConnecting(false);
        setRemotePresent(room.remoteParticipants.size > 0);

        void (async () => {
          try {
            const publication = await room.localParticipant.setCameraEnabled(true);
            attachLocalVideo(publication);
          } catch {
            if (!cancelled) {
              setCameraOn(false);
              setMediaNote("Камера недоступна — звонок идёт без видео.");
            }
          }
          try {
            await room.localParticipant.setMicrophoneEnabled(true);
          } catch {
            if (!cancelled) {
              setMicOn(false);
              setMediaNote((previous) =>
                previous ?? "Микрофон недоступен — вас не будет слышно.",
              );
            }
          }
        })();
      } catch (connectionError) {
        if (!cancelled) {
          setError(
            connectionError instanceof Error
              ? connectionError.message
              : "Не удалось подключиться к видеозвонку.",
          );
        }
      } finally {
        if (!cancelled) {
          setConnecting(false);
        }
      }
    }

    function attachLocalVideo(publication?: LocalTrackPublication) {
      const track = publication?.track;
      if (track && localVideoRef.current) {
        track.attach(localVideoRef.current);
      }
    }

    void connect();

    return () => {
      cancelled = true;
      room.off(RoomEvent.TrackSubscribed, onTrackSubscribed);
      room.off(RoomEvent.TrackUnsubscribed, onTrackUnsubscribed);
      room.off(RoomEvent.ParticipantConnected, onParticipantConnected);
      room.off(RoomEvent.ParticipantDisconnected, onParticipantDisconnected);
      room.off(RoomEvent.Disconnected, onDisconnected);
      void room.disconnect();
      roomRef.current = null;
      if (remoteVideoRef.current) {
        remoteVideoRef.current.srcObject = null;
      }
      if (audioContainerRef.current) {
        audioContainerRef.current.innerHTML = "";
      }
    };
  }, [join, open]);

  useEffect(() => {
    if (!connected) {
      return;
    }
    const timer = window.setInterval(() => setSeconds((value) => value + 1), 1000);
    return () => window.clearInterval(timer);
  }, [connected]);

  async function toggleMicrophone() {
    const room = roomRef.current;
    if (!room) {
      return;
    }
    try {
      await room.localParticipant.setMicrophoneEnabled(!micOn);
      setMicOn((value) => !value);
    } catch {
      setError("Не удалось переключить микрофон.");
    }
  }

  async function toggleCamera() {
    const room = roomRef.current;
    if (!room) {
      return;
    }
    try {
      const publication = await room.localParticipant.setCameraEnabled(!cameraOn);
      setCameraOn((value) => !value);
      if (publication?.track && localVideoRef.current) {
        publication.track.attach(localVideoRef.current);
      }
    } catch {
      setError("Не удалось переключить камеру.");
    }
  }

  async function finishCall() {
    if (!join) {
      return;
    }
    setEnding(true);
    try {
      if (canEnd) {
        await endCall(join.call.id);
        onEnded?.();
      }
    } catch (requestError) {
      setError(formatApiError(requestError));
    } finally {
      roomRef.current?.disconnect();
      setEnding(false);
      onClose();
    }
  }

  function onMiniPointerDown(event: ReactPointerEvent<HTMLDivElement>) {
    if (event.button !== 0) {
      return;
    }
    const rect = miniRef.current?.getBoundingClientRect();
    if (!rect) {
      return;
    }
    dragOffset.current = {
      x: event.clientX - rect.left,
      y: event.clientY - rect.top,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
  }

  function onMiniPointerMove(event: ReactPointerEvent<HTMLDivElement>) {
    if (!event.currentTarget.hasPointerCapture(event.pointerId)) {
      return;
    }
    const rect = miniRef.current?.getBoundingClientRect();
    const width = rect?.width ?? 320;
    const height = rect?.height ?? 120;
    const maxX = Math.max(8, window.innerWidth - width - 8);
    const maxY = Math.max(8, window.innerHeight - height - 8);
    setPosition({
      x: Math.min(Math.max(8, event.clientX - dragOffset.current.x), maxX),
      y: Math.min(Math.max(8, event.clientY - dragOffset.current.y), maxY),
    });
  }

  function onMiniPointerUp(event: ReactPointerEvent<HTMLDivElement>) {
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  }

  if (!open || !join) {
    return null;
  }

  return createPortal(
    <>
      <div
        className={cn(
          "fixed inset-0 z-[60] flex items-center justify-center p-4",
          minimized && "hidden",
        )}
      >
        <div
          className="absolute inset-0 bg-black/80 backdrop-blur-sm"
          onClick={() => setMinimized(true)}
        />
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Видеозвонок"
        className="relative flex w-full max-w-4xl flex-col overflow-hidden rounded-xl border bg-card shadow-2xl"
      >
        <header className="flex items-center justify-between gap-3 border-b px-5 py-3">
          <div>
            <h2 className="text-base font-semibold">Видеозвонок</h2>
            <p className="text-sm text-muted-foreground">
              {counterpartName} · {connected ? formatDuration(seconds) : "подключение…"}
            </p>
          </div>
          <div className="flex items-center gap-1">
            <Button
              type="button"
              variant="ghost"
              size="icon"
              onClick={() => setMinimized(true)}
              aria-label="Свернуть звонок"
              title="Свернуть"
            >
              <Minimize2 className="h-4 w-4" />
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              onClick={onClose}
              aria-label="Закрыть звонок"
            >
              <X className="h-4 w-4" />
            </Button>
          </div>
        </header>

        <div className="relative aspect-video w-full bg-black">
          <video
            ref={remoteVideoRef}
            autoPlay
            playsInline
            className="h-full w-full object-cover"
          />
          {!remotePresent ? (
            <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 text-sm text-white/70">
              <span className="flex h-14 w-14 items-center justify-center rounded-full bg-white/10">
                <Video className="h-6 w-6" />
              </span>
              {connecting ? "Подключаемся к комнате…" : "Ожидаем подключения собеседника"}
            </div>
          ) : null}

          <div className="absolute bottom-3 right-3 h-24 w-32 overflow-hidden rounded-lg border border-white/20 bg-black/60">
            <video
              ref={localVideoRef}
              autoPlay
              muted
              playsInline
              className={cn("h-full w-full object-cover", cameraOn ? null : "opacity-0")}
            />
            {!cameraOn ? (
              <div className="absolute inset-0 flex items-center justify-center text-xs text-white/70">
                Камера выключена
              </div>
            ) : null}
          </div>
          <div ref={audioContainerRef} className="hidden" />
        </div>

        <div className="space-y-2 px-5 py-4">
          {error ? (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}
          {mediaNote ? (
            <p className="text-xs text-muted-foreground">{mediaNote}</p>
          ) : null}

          <div className="flex items-center justify-center gap-3">
            <Button
              type="button"
              variant={micOn ? "outline" : "secondary"}
              size="icon"
              className="h-11 w-11 rounded-full"
              onClick={() => void toggleMicrophone()}
              aria-label={micOn ? "Выключить микрофон" : "Включить микрофон"}
            >
              {micOn ? <Mic className="h-5 w-5" /> : <MicOff className="h-5 w-5" />}
            </Button>
            <Button
              type="button"
              variant={cameraOn ? "outline" : "secondary"}
              size="icon"
              className="h-11 w-11 rounded-full"
              onClick={() => void toggleCamera()}
              aria-label={cameraOn ? "Выключить камеру" : "Включить камеру"}
            >
              {cameraOn ? <Video className="h-5 w-5" /> : <VideoOff className="h-5 w-5" />}
            </Button>
            <Button
              type="button"
              variant="destructive"
              className="h-11 rounded-full px-5"
              disabled={ending}
              onClick={() => void finishCall()}
            >
              <PhoneOff className="h-4 w-4" />
              {canEnd ? "Завершить" : "Выйти"}
            </Button>
          </div>
        </div>
      </div>
      </div>

      {minimized ? (
        <div
          ref={miniRef}
          style={{ left: position.x, top: position.y }}
          className="fixed z-[61] w-[min(20rem,calc(100vw-2rem))] rounded-xl border bg-card p-3 shadow-2xl"
        >
          <div className="flex items-center gap-3">
            <div
              className="flex min-w-0 flex-1 cursor-grab touch-none select-none items-center gap-3 active:cursor-grabbing"
              onPointerDown={onMiniPointerDown}
              onPointerMove={onMiniPointerMove}
              onPointerUp={onMiniPointerUp}
              onPointerCancel={onMiniPointerUp}
              title="Перетащите, чтобы переместить"
            >
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                <Video className="h-4 w-4" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{counterpartName}</p>
                <p className="text-xs text-muted-foreground">
                  {connected ? formatDuration(seconds) : "Подключение…"}
                </p>
              </div>
            </div>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              onClick={() => setMinimized(false)}
              aria-label="Развернуть звонок"
              title="Развернуть"
            >
              <Maximize2 className="h-4 w-4" />
            </Button>
          </div>
          <div className="mt-3 flex items-center gap-2">
            <Button
              type="button"
              variant={micOn ? "outline" : "secondary"}
              size="icon"
              className="h-9 w-9 rounded-full"
              onClick={() => void toggleMicrophone()}
              aria-label={micOn ? "Выключить микрофон" : "Включить микрофон"}
            >
              {micOn ? <Mic className="h-4 w-4" /> : <MicOff className="h-4 w-4" />}
            </Button>
            <Button
              type="button"
              variant={cameraOn ? "outline" : "secondary"}
              size="icon"
              className="h-9 w-9 rounded-full"
              onClick={() => void toggleCamera()}
              aria-label={cameraOn ? "Выключить камеру" : "Включить камеру"}
            >
              {cameraOn ? <Video className="h-4 w-4" /> : <VideoOff className="h-4 w-4" />}
            </Button>
            <Button
              type="button"
              variant="destructive"
              size="sm"
              className="ml-auto rounded-full"
              disabled={ending}
              onClick={() => void finishCall()}
            >
              <PhoneOff className="h-4 w-4" />
              {canEnd ? "Завершить" : "Выйти"}
            </Button>
          </div>
        </div>
      ) : null}
    </>,
    document.body,
  );
}

function formatDuration(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}
