"""Публикация уведомлений о начале звонка через Redis pub/sub."""

from __future__ import annotations

import json
import logging
from typing import Any

import redis.asyncio as aioredis

from video_service.application.dto import CallView

logger = logging.getLogger(__name__)


class RedisCallNotifier:
    """Отправляет пациенту событие ``call.started``.

    Используется тот же канал ``user:<patient_id>``, на который подписан
    chat-service для WebSocket-доставки. Поэтому пациент получает уведомление
    в реальном времени через уже открытый чат-сокет, без polling.

    Уведомления best-effort: ошибка Redis не должна ломать создание звонка.
    """

    def __init__(self, url: str) -> None:
        self._url = url
        self._client: aioredis.Redis | None = None

    async def _get_client(self) -> aioredis.Redis:
        if self._client is None:
            self._client = aioredis.from_url(
                self._url,
                decode_responses=True,
                socket_connect_timeout=2,
                socket_timeout=2,
            )
        return self._client

    async def notify_started(self, call: CallView) -> None:
        if not self._url:
            return

        payload: dict[str, Any] = {
            "type": "call.started",
            "payload": {
                "call_id": call.id,
                "appointment_id": call.appointment_id,
                "room_name": call.room_name,
                "doctor_id": call.doctor_id,
                "patient_id": call.patient_id,
                "status": call.status,
                "created_at": call.created_at.isoformat(),
            },
        }

        try:
            client = await self._get_client()
            await client.publish(f"user:{call.patient_id}", json.dumps(payload))
        except Exception:
            logger.exception("call.notify.redis_failed", extra={"call_id": call.id})

    async def aclose(self) -> None:
        if self._client is not None:
            try:
                await self._client.aclose()
            finally:
                self._client = None
