"""Redis adapters: JSON cache and cached file metadata."""

from __future__ import annotations

import json
import logging
from datetime import datetime
from typing import Any
from uuid import UUID

from redis.asyncio import Redis
from redis.exceptions import RedisError

from file_service.application.dto import FileInfo
from file_service.application.ports import FileCache
from file_service.domain.enums import FileKind, FileStatus

logger = logging.getLogger(__name__)


class RedisJsonCache:
    """Small fail-open JSON cache. Redis outages must not take the service down."""

    def __init__(self, url: str) -> None:
        self._redis = Redis.from_url(url, decode_responses=True, socket_timeout=2, socket_connect_timeout=2)

    async def get_json(self, key: str) -> dict[str, Any] | None:
        try:
            raw = await self._redis.get(key)
        except RedisError:
            logger.warning("redis get failed", exc_info=True)
            return None
        if raw is None:
            return None
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return None

    async def set_json(self, key: str, value: dict[str, Any], ttl_seconds: int) -> None:
        try:
            await self._redis.set(key, json.dumps(value, separators=(",", ":")), ex=max(ttl_seconds, 1))
        except RedisError:
            logger.warning("redis set failed", exc_info=True)

    async def delete(self, key: str) -> None:
        try:
            await self._redis.delete(key)
        except RedisError:
            logger.warning("redis delete failed", exc_info=True)

    async def ping(self) -> bool:
        try:
            return bool(await self._redis.ping())
        except RedisError:
            return False

    async def close(self) -> None:
        await self._redis.aclose()


class RedisFileCache(FileCache):
    def __init__(self, cache: RedisJsonCache, *, prefix: str = "file:meta:") -> None:
        self._cache = cache
        self._prefix = prefix

    def _key(self, file_id: UUID) -> str:
        return f"{self._prefix}{file_id}"

    async def get(self, file_id: UUID) -> FileInfo | None:
        data = await self._cache.get_json(self._key(file_id))
        if not data:
            return None
        try:
            return FileInfo(
                id=UUID(data["id"]),
                chat_id=UUID(data["chat_id"]),
                uploader_id=UUID(data["uploader_id"]),
                file_name=data["file_name"],
                content_type=data["content_type"],
                kind=FileKind(data["kind"]),
                size_bytes=int(data["size_bytes"]),
                status=FileStatus(data["status"]),
                storage_key=data["storage_key"],
                checksum_sha256=data.get("checksum_sha256"),
                message_id=UUID(data["message_id"]) if data.get("message_id") else None,
                created_at=datetime.fromisoformat(data["created_at"]),
            )
        except (KeyError, ValueError, TypeError):
            logger.warning("invalid cached file metadata, ignoring", extra={"file_id": str(file_id)})
            return None

    async def set(self, info: FileInfo, *, ttl_seconds: int) -> None:
        await self._cache.set_json(
            self._key(info.id),
            {
                "id": str(info.id),
                "chat_id": str(info.chat_id),
                "uploader_id": str(info.uploader_id),
                "file_name": info.file_name,
                "content_type": info.content_type,
                "kind": info.kind.value,
                "size_bytes": info.size_bytes,
                "status": info.status.value,
                "storage_key": info.storage_key,
                "checksum_sha256": info.checksum_sha256,
                "message_id": str(info.message_id) if info.message_id else None,
                "created_at": info.created_at.isoformat(),
            },
            ttl_seconds=ttl_seconds,
        )

    async def delete(self, file_id: UUID) -> None:
        await self._cache.delete(self._key(file_id))

    async def healthy(self) -> bool:
        return await self._cache.ping()
