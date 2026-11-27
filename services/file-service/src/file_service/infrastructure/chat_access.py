"""Chat membership checks delegated to ChatService, with a Redis cache."""

from __future__ import annotations

import logging
from uuid import UUID

import httpx

from file_service.application.dto import Actor
from file_service.application.ports import ChatAccessPolicy
from file_service.domain.exceptions import AccessCheckUnavailableError
from file_service.infrastructure.cache.redis_cache import RedisJsonCache

logger = logging.getLogger(__name__)


class AllowAllChatAccessPolicy(ChatAccessPolicy):
    """Used for local demo / tests when CHAT_SERVICE_URL is empty."""

    async def can_access(self, actor: Actor, chat_id: UUID) -> bool:
        return True

    async def aclose(self) -> None:
        return None


class DenyAllChatAccessPolicy(ChatAccessPolicy):
    """Fail-closed fallback used when ChatService URL is not configured."""

    async def can_access(self, actor: Actor, chat_id: UUID) -> bool:
        logger.error(
            "chat access denied: CHAT_SERVICE_URL is not configured",
            extra={"chat_id": str(chat_id), "user_id": str(actor.id)},
        )
        return False

    async def aclose(self) -> None:
        return None


class HttpChatAccessPolicy(ChatAccessPolicy):
    def __init__(
        self,
        *,
        base_url: str,
        internal_token: str,
        cache: RedisJsonCache,
        cache_ttl_seconds: int = 60,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._internal_token = internal_token
        self._cache = cache
        self._cache_ttl_seconds = cache_ttl_seconds
        self._client = httpx.AsyncClient(
            base_url=self._base_url,
            timeout=httpx.Timeout(3.0, connect=2.0),
            headers={"X-Internal-Token": internal_token} if internal_token else {},
        )

    async def can_access(self, actor: Actor, chat_id: UUID) -> bool:
        if actor.is_moderator:
            return True
        cache_key = f"chat-access:{chat_id}:{actor.id}"
        cached = await self._cache.get_json(cache_key)
        if cached is not None:
            return bool(cached.get("allowed"))

        url = f"/internal/chat/rooms/{chat_id}/members/{actor.id}"
        try:
            response = await self._client.get(url)
        except httpx.HTTPError as exc:
            raise AccessCheckUnavailableError("ChatService is unavailable") from exc

        if response.status_code == 200:
            allowed = True
        elif response.status_code in {403, 404}:
            allowed = False
        elif response.status_code >= 500:
            raise AccessCheckUnavailableError("ChatService returned an error")
        else:
            logger.warning("unexpected ChatService response", extra={"status": response.status_code})
            raise AccessCheckUnavailableError("Cannot verify chat membership")

        await self._cache.set_json(cache_key, {"allowed": allowed}, ttl_seconds=self._cache_ttl_seconds)
        return allowed

    async def aclose(self) -> None:
        await self._client.aclose()
