import json
from typing import Any
from uuid import UUID

import redis.asyncio as aioredis


class RedisEventPublisher:
    def __init__(self, redis: aioredis.Redis) -> None:
        self._r = redis

    async def publish_to_user(self, user_id: UUID, event: dict[str, Any]) -> None:
        await self._r.publish(f"user:{user_id}", json.dumps(event, default=str))

    async def publish_to_users(self, user_ids: list[UUID], event: dict[str, Any]) -> None:
        payload = json.dumps(event, default=str)
        for uid in user_ids:
            await self._r.publish(f"user:{uid}", payload)