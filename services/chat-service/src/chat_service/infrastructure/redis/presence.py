from uuid import UUID

import redis.asyncio as aioredis


class RedisPresenceService:
    TTL = 60

    def __init__(self, redis: aioredis.Redis) -> None:
        self._r = redis

    async def set_online(self, user_id: UUID) -> None:
        await self._r.incr(f"presence:{user_id}")
        await self._r.expire(f"presence:{user_id}", self.TTL)

    async def set_offline(self, user_id: UUID) -> None:
        key = f"presence:{user_id}"
        val = await self._r.decr(key)
        if val <= 0:
            await self._r.delete(key)

    async def is_online(self, user_id: UUID) -> bool:
        return bool(await self._r.exists(f"presence:{user_id}"))