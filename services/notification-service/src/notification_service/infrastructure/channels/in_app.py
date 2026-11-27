"""In-app delivery via Redis pub/sub.

The patient's chat WebSocket already subscribes to ``user:<id>`` and forwards
every message it receives, so NotificationService publishes a
``notification.created`` event there.
"""

from __future__ import annotations

import json
import logging

import redis.asyncio as aioredis

logger = logging.getLogger(__name__)


class RedisInAppPublisher:
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

    async def publish(self, *, user_id: str, message: dict) -> None:
        client = await self._get_client()
        await client.publish(f"user:{user_id}", json.dumps(message, ensure_ascii=False))

    async def ping(self) -> bool:
        try:
            client = await self._get_client()
            return bool(await client.ping())
        except Exception:
            logger.warning("notification.redis.ping_failed", exc_info=True)
            return False

    async def close(self) -> None:
        if self._client is not None:
            try:
                await self._client.aclose()
            finally:
                self._client = None
