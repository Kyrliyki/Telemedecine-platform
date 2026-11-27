"""Значимые объекты (value objects) домена видеозвонков."""

from __future__ import annotations

import re
import uuid
from dataclasses import dataclass

_ROOM_NAME_RE = re.compile(r"^[A-Za-z0-9_-]{1,64}$")

@dataclass(frozen=True, slots=True)
class CallId:
    """Идентификатор звонка."""

    value: uuid.UUID

    @classmethod
    def new(cls) -> CallId:
        return cls(uuid.uuid4())

    @classmethod
    def parse(cls, raw: str) -> CallId:
        try:
            return cls(uuid.UUID(raw))
        except (ValueError, AttributeError) as exc:
            raise ValueError("call_id должен быть валидным UUID") from exc

    def __str__(self) -> str:
        return str(self.value)

@dataclass(frozen=True, slots=True)
class RoomName:
    """Имя комнаты LiveKit. Допустимые символы: A-Z, a-z, 0-9, ``_`` и ``-``."""

    value: str

    def __post_init__(self) -> None:
        if not _ROOM_NAME_RE.match(self.value):
            raise ValueError(
                "room_name должен содержать 1-64 символа [A-Za-z0-9_-]",
            )

    @classmethod
    def for_call(cls, call_id: CallId) -> RoomName:
        
        return cls(f"call_{call_id.value.hex}")

    def __str__(self) -> str:
        return self.value

@dataclass(frozen=True, slots=True)
class ParticipantIdentity:
    """Идентификатор участника внутри комнаты (обычно ``sub`` из JWT)."""

    value: str

    def __post_init__(self) -> None:
        if not self.value or len(self.value) > 128:
            raise ValueError("identity должен содержать 1-128 символов")
        if any(ch.isspace() for ch in self.value):
            raise ValueError("identity не должен содержать пробелов")

    def __str__(self) -> str:
        return self.value
