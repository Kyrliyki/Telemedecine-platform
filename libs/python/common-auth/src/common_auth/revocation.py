"""Token revocation checks against the AuthService Redis store.

AuthService writes:
- ``blacklist:{jti}`` for explicitly logged-out access tokens;
- ``auth:revoked-before:{userId}`` for block/delete/role-change cutoffs.

The checker is fail-closed: if Redis is unavailable, authentication returns 503.
"""

from __future__ import annotations

import logging

import redis.asyncio as aioredis
from prometheus_client import Counter
from redis.exceptions import RedisError

logger = logging.getLogger(__name__)

REVOCATION_CHECK_FAILURES = Counter(
    "auth_revocation_check_failures_total",
    "Number of token revocation checks that failed because Redis was unavailable",
)

BLACKLIST_PREFIX = "blacklist:"
USER_REVOKED_PREFIX = "auth:revoked-before:"


class TokenRevokedError(Exception):
    """The token is blacklisted or issued before the user's revocation cutoff."""


class RevocationStoreUnavailableError(Exception):
    """Redis cannot be reached, so revocation cannot be verified."""


class TokenRevocationChecker:
    def __init__(
        self,
        redis_url: str,
        *,
        socket_connect_timeout: float = 2.0,
        socket_timeout: float = 2.0,
    ) -> None:
        if not redis_url:
            raise ValueError("redis_url is required")
        self._redis = aioredis.from_url(
            redis_url,
            decode_responses=True,
            socket_connect_timeout=socket_connect_timeout,
            socket_timeout=socket_timeout,
        )

    async def ensure_active(self, *, subject: str, jti: str | None, issued_at_epoch: float) -> None:
        try:
            async with self._redis.pipeline(transaction=False) as pipe:
                if jti:
                    pipe.get(f"{BLACKLIST_PREFIX}{jti}")
                else:
                    pipe.get("__missing_jti__")
                pipe.get(f"{USER_REVOKED_PREFIX}{subject}")
                blacklisted, cutoff = await pipe.execute()
        except RedisError as exc:
            REVOCATION_CHECK_FAILURES.inc()
            logger.error(
                "token revocation check failed: Redis is unavailable",
                extra={"subject": subject, "error": str(exc)},
            )
            raise RevocationStoreUnavailableError("Token revocation store is unavailable") from exc

        if blacklisted:
            raise TokenRevokedError("Token has been revoked")

        if cutoff:
            try:
                cutoff_millis = int(cutoff)
            except (TypeError, ValueError):
                cutoff_millis = 0
            if issued_at_epoch * 1000 < cutoff_millis:
                raise TokenRevokedError("Token was invalidated by an account or role change")

    async def aclose(self) -> None:
        await self._redis.aclose()
