"""Redis cache adapter with graceful degradation.

If Redis is temporarily unavailable the service keeps working: reads fall through
to PostgreSQL and writes only produce a warning in the log.
"""

from __future__ import annotations

import json
from contextlib import suppress
from typing import Any

from redis.asyncio import Redis
from redis.exceptions import RedisError

from appointment.infrastructure.logging import get_logger

logger = get_logger(__name__)


class RedisCache:
    def __init__(self, client: Redis, *, default_ttl: int = 60) -> None:
        self._client = client
        self._default_ttl = default_ttl

    @classmethod
    def from_url(cls, url: str, *, default_ttl: int = 60) -> RedisCache:
        client = Redis.from_url(
            url,
            decode_responses=True,
            socket_connect_timeout=1.0,
            socket_timeout=1.0,
            health_check_interval=30,
        )
        return cls(client, default_ttl=default_ttl)

    async def get_json(self, key: str) -> Any | None:
        try:
            raw = await self._client.get(key)
        except RedisError as exc:
            logger.warning("cache_get_failed", key=key, error=str(exc))
            return None
        if raw is None:
            return None
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            logger.warning("cache_value_corrupted", key=key)
            return None

    async def set_json(self, key: str, value: Any, ttl_seconds: int | None = None) -> None:
        try:
            await self._client.set(
                key,
                json.dumps(value, separators=(",", ":")),
                ex=ttl_seconds or self._default_ttl,
            )
        except RedisError as exc:
            logger.warning("cache_set_failed", key=key, error=str(exc))

    async def delete(self, key: str) -> None:
        try:
            await self._client.delete(key)
        except RedisError as exc:
            logger.warning("cache_delete_failed", key=key, error=str(exc))

    async def ping(self) -> bool:
        try:
            return bool(await self._client.ping())
        except RedisError:
            return False

    async def close(self) -> None:
        with suppress(RedisError):
            await self._client.aclose()


class NullCache:
    """No-op cache used when Redis is disabled (tests, minimal local setup)."""

    async def get_json(self, key: str) -> Any | None:
        return None

    async def set_json(self, key: str, value: Any, ttl_seconds: int | None = None) -> None:
        return None

    async def delete(self, key: str) -> None:
        return None

    async def ping(self) -> bool:
        return True

    async def close(self) -> None:
        return None
