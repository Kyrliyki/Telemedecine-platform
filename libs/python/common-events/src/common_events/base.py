"""Base classes for integration-event payloads.

Java services serialize payloads as camelCase JSON. Python payload models keep
snake_case attribute names and expose camelCase aliases, so the same contract
works on both sides.
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict


def to_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part.capitalize() for part in tail)


class EventPayload(BaseModel):
    """Base model for a service-specific event payload.

    - ``populate_by_name=True`` lets Python code use snake_case;
    - ``alias_generator=to_camel`` accepts and produces Java camelCase;
    - ``extra="ignore"`` keeps consumers forward compatible when a producer
      adds a new optional field.
    """

    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="ignore",
    )

    def to_payload(self) -> dict[str, Any]:
        """Return a JSON-ready camelCase payload for :class:`EventEnvelope`."""

        return self.model_dump(by_alias=True, mode="json")
