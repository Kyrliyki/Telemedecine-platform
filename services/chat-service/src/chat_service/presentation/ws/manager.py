import asyncio
import json
import logging
from collections import defaultdict
from uuid import UUID

import redis.asyncio as aioredis
from fastapi import WebSocket

log = logging.getLogger("chat.ws")


class ConnectionManager:
    """Держит WS-соединения по user_id + подписки Redis pub/sub."""

    def __init__(self, redis: aioredis.Redis) -> None:
        self._redis = redis
        self._connections: dict[UUID, set[WebSocket]] = defaultdict(set)
        self._locks: dict[UUID, asyncio.Lock] = {}
        self._tasks: dict[UUID, asyncio.Task] = {}

    async def connect(self, user_id: UUID, ws: WebSocket) -> None:
        await ws.accept()
        self._connections[user_id].add(ws)
        if user_id not in self._tasks or self._tasks[user_id].done():
            self._tasks[user_id] = asyncio.create_task(self._subscribe(user_id))
        log.info("ws.connect", extra={"user_id": str(user_id)})

    async def disconnect(self, user_id: UUID, ws: WebSocket) -> None:
        self._connections[user_id].discard(ws)
        if not self._connections[user_id]:
            task = self._tasks.pop(user_id, None)
            if task:
                task.cancel()
        log.info("ws.disconnect", extra={"user_id": str(user_id)})

    async def send_direct(self, user_id: UUID, event: dict) -> None:
        payload = json.dumps(event, default=str)
        for ws in list(self._connections.get(user_id, [])):
            try:
                await ws.send_text(payload)
            except Exception:
                self._connections[user_id].discard(ws)

    async def _subscribe(self, user_id: UUID) -> None:
        pubsub = self._redis.pubsub()
        await pubsub.subscribe(f"user:{user_id}")
        try:
            async for msg in pubsub.listen():
                if msg["type"] != "message":
                    continue
                payload = msg["data"]
                for ws in list(self._connections.get(user_id, [])):
                    try:
                        await ws.send_text(payload)
                    except Exception:
                        self._connections[user_id].discard(ws)
        finally:
            await pubsub.unsubscribe(f"user:{user_id}")
            await pubsub.close()