"""Реализация порта LiveKitGateway поверх официального livekit-api."""

from __future__ import annotations

import json
import logging
from datetime import timedelta

from livekit import api

from video_service.application.ports import LiveKitGatewayError
from video_service.domain.value_objects import RoomName

logger = logging.getLogger(__name__)


class LiveKitGatewayAdapter:
    def __init__(
        self,
        *,
        url: str,
        api_key: str,
        api_secret: str,
        empty_timeout_seconds: int = 300,
        departure_timeout_seconds: int = 20,
    ) -> None:
        self._url = url
        self._api: api.LiveKitAPI | None = None
        self._api_key = api_key
        self._api_secret = api_secret
        self._empty_timeout = empty_timeout_seconds
        self._departure_timeout = departure_timeout_seconds

    def _client(self) -> api.LiveKitAPI:
        """Ленивая инициализация: aiohttp-сессия создаётся внутри event loop."""
        if self._api is None:
            self._api = api.LiveKitAPI(self._url, self._api_key, self._api_secret)
        return self._api

    async def create_room(
        self,
        *,
        room_name: RoomName,
        metadata: dict[str, str],
        max_participants: int,
    ) -> None:
        try:
            await self._client().room.create_room(
                api.CreateRoomRequest(
                    name=room_name.value,
                    empty_timeout=self._empty_timeout,
                    departure_timeout=self._departure_timeout,
                    max_participants=max_participants,
                    metadata=json.dumps(metadata, ensure_ascii=False),
                ),
            )
        except Exception as exc:
            logger.exception("livekit.create_room_failed", extra={"room_name": room_name.value})
            raise LiveKitGatewayError(
                f"LiveKit не создал комнату {room_name.value}: {exc}",
            ) from exc

    async def delete_room(self, *, room_name: RoomName) -> None:
        try:
            await self._client().room.delete_room(
                api.DeleteRoomRequest(room=room_name.value),
            )
        except Exception as exc:
            if self._is_not_found(exc):
                return
            logger.exception("livekit.delete_room_failed", extra={"room_name": room_name.value})
            raise LiveKitGatewayError(
                f"LiveKit не удалил комнату {room_name.value}: {exc}",
            ) from exc

    async def create_participant_token(
        self,
        *,
        room_name: RoomName,
        identity: str,
        display_name: str,
        ttl: timedelta,
        metadata: dict[str, str],
    ) -> str:
        try:
            return (
                api.AccessToken(self._api_key, self._api_secret)
                .with_identity(identity)
                .with_name(display_name)
                .with_ttl(ttl)
                .with_metadata(json.dumps(metadata, ensure_ascii=False))
                .with_grants(
                    api.VideoGrants(
                        room_join=True,
                        room=room_name.value,
                        can_publish=True,
                        can_subscribe=True,
                        can_publish_data=True,
                    ),
                )
                .to_jwt()
            )
        except Exception as exc:
            logger.exception("livekit.token_failed", extra={"room_name": room_name.value})
            raise LiveKitGatewayError("Не удалось сформировать токен LiveKit") from exc

    async def healthcheck(self) -> bool:
        try:
            await self._client().room.list_rooms(api.ListRoomsRequest())
            return True
        except Exception:
            logger.warning("livekit.healthcheck_failed", exc_info=True)
            return False

    async def aclose(self) -> None:
        if self._api is not None:
            await self._api.aclose()
            self._api = None

    @staticmethod
    def _is_not_found(exc: Exception) -> bool:
        code = getattr(exc, "code", "")
        return str(code).lower() in {"not_found", "404"} or "not found" in str(exc).lower()
