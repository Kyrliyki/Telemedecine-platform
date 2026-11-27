"""Central registry of integration-event payloads.

Java producers are free to add fields; consumers should parse through this
registry so unknown or malformed events fail in one predictable place.
"""

from __future__ import annotations

from typing import Any

from .appointment import APPOINTMENT_EVENT_PAYLOADS
from .auth import AUTH_EVENT_PAYLOADS
from .base import EventPayload
from .chat import CHAT_EVENT_PAYLOADS
from .emr import EMR_EVENT_PAYLOADS
from .file import FILE_EVENT_PAYLOADS
from .notification import NOTIFICATION_EVENT_PAYLOADS
from .schedule import SCHEDULE_EVENT_PAYLOADS
from .user import USER_EVENT_PAYLOADS
from .video import VIDEO_EVENT_PAYLOADS

ALL_EVENT_PAYLOADS: dict[str, type[EventPayload]] = {
    **AUTH_EVENT_PAYLOADS,
    **APPOINTMENT_EVENT_PAYLOADS,
    **CHAT_EVENT_PAYLOADS,
    **EMR_EVENT_PAYLOADS,
    **FILE_EVENT_PAYLOADS,
    **NOTIFICATION_EVENT_PAYLOADS,
    **SCHEDULE_EVENT_PAYLOADS,
    **USER_EVENT_PAYLOADS,
    **VIDEO_EVENT_PAYLOADS,
}


def parse_payload(event_type: str, payload: dict[str, Any]) -> EventPayload:
    """Validate a payload using the model registered for ``event_type``."""

    model = ALL_EVENT_PAYLOADS.get(event_type)
    if model is None:
        raise ValueError(f"Unknown event type: {event_type}")
    return model.model_validate(payload)


def event_types_for(service: str) -> list[str]:
    """Return every known event type belonging to ``service``."""

    prefix = f"{service}."
    return sorted(name for name in ALL_EVENT_PAYLOADS if name.startswith(prefix))
