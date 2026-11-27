"""Системные реализации портов времени и идентификаторов."""

from __future__ import annotations

from datetime import UTC, datetime

from video_service.domain.value_objects import CallId


class SystemClock:
    def now(self) -> datetime:
        return datetime.now(UTC)


class Uuid4Generator:
    def new_call_id(self) -> CallId:
        return CallId.new()
