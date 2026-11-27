from __future__ import annotations

import re
from dataclasses import dataclass
from enum import Enum


class RequestStatus(str, Enum):  # noqa: UP042
    OPEN = "open"
    CLOSED = "closed"


class ParticipantRole(str, Enum):  # noqa: UP042
    CLIENT = "client"
    OPERATOR = "operator"


_REQUEST_ID_RE = re.compile(r"^[A-Za-z0-9_\-:.]{1,255}$")


@dataclass(frozen=True, slots=True)
class RequestId:
    value: str

    def __post_init__(self) -> None:
        if not _REQUEST_ID_RE.match(self.value):
            raise ValueError(f"invalid request_id: {self.value!r}")


@dataclass(frozen=True, slots=True)
class MessageContent:
    value: str

    def __post_init__(self) -> None:
        stripped = self.value.strip()
        if not stripped:
            raise ValueError("message content is empty")
        if len(stripped) > 4096:
            raise ValueError("message content exceeds 4096 chars")