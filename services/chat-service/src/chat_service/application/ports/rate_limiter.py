from typing import Protocol
from uuid import UUID


class RateLimiter(Protocol):
    async def check(self, user_id: UUID) -> bool: ...  