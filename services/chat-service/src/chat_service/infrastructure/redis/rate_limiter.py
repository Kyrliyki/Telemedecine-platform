import time
from uuid import UUID

import redis.asyncio as aioredis


class RedisSlidingWindowRateLimiter:
    """Sliding window 60s."""

    def __init__(self, redis: aioredis.Redis, limit_per_min: int) -> None:
        self._r = redis
        self._limit = limit_per_min

    async def check(self, user_id: UUID) -> bool:
        key = f"ratelimit:{user_id}"
        now = time.time()
        window_start = now - 60
        pipe = self._r.pipeline()
        pipe.zremrangebyscore(key, 0, window_start)
        pipe.zadd(key, {str(now): now})
        pipe.zcard(key)
        pipe.expire(key, 60)
        _, _, count, _ = await pipe.execute()
        return count <= self._limit