"""Проверка и разбор вебхуков LiveKit."""

from __future__ import annotations

import logging

from livekit import api

from video_service.application.ports import (
    LiveKitRoomEvent,
    RoomEventKind,
    WebhookVerificationError,
)
from video_service.domain.value_objects import RoomName

logger = logging.getLogger(__name__)

_EVENT_KINDS = {
    "room_started": RoomEventKind.ROOM_STARTED,
    "room_finished": RoomEventKind.ROOM_FINISHED,
}


class LiveKitWebhookParser:
    """Проверяет JWT-подпись вебхука и хеш тела запроса через SDK LiveKit."""

    def __init__(self, *, api_key: str, api_secret: str) -> None:
        self._receiver = api.WebhookReceiver(api.TokenVerifier(api_key, api_secret))

    def parse(self, body: bytes, auth_header: str) -> LiveKitRoomEvent | None:
        try:
            event = self._receiver.receive(body.decode("utf-8"), auth_header)
        except Exception as exc:
            logger.warning("livekit.webhook.invalid_signature", exc_info=True)
            raise WebhookVerificationError("Невалидная подпись вебхука") from exc

        kind = _EVENT_KINDS.get(event.event)
        if kind is None:
            return None

        room = getattr(event, "room", None)
        room_name = getattr(room, "name", "") if room is not None else ""
        if not room_name:
            logger.warning("livekit.webhook.room_missing", extra={"event": event.event})
            return None

        try:
            return LiveKitRoomEvent(
                kind=kind,
                room_name=RoomName(room_name),
                event_id=event.id or "",
            )
        except ValueError:
            logger.warning("livekit.webhook.invalid_room_name", extra={"room_name": room_name})
            return None
