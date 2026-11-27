"""Wire-compatible event envelope for Java and Python services.

The Java ``IntegrationEventEnvelope`` publishes only the payload JSON as the
message body and carries metadata in AMQP headers. This module mirrors that
contract exactly.
"""

from __future__ import annotations

import json
from collections.abc import Mapping
from datetime import UTC, datetime
from typing import Any
from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field

CORRELATION_HEADER = "X-Correlation-Id"
EVENT_ID_HEADER = "eventId"
EVENT_TYPE_HEADER = "eventType"
EVENT_VERSION_HEADER = "eventVersion"


def utcnow() -> datetime:
    return datetime.now(UTC)


def _json_default(value: Any) -> Any:
    if isinstance(value, datetime):
        return value.isoformat()
    if isinstance(value, UUID):
        return str(value)
    raise TypeError(f"Object of type {type(value).__name__} is not JSON serializable")


def _header(value: Any) -> str | None:
    if value is None:
        return None
    if isinstance(value, bytes):
        return value.decode("utf-8")
    return str(value)


class EventEnvelope(BaseModel):
    """One integration event.

    ``payload`` is serialized to JSON and becomes the AMQP message body.
    Metadata is written to headers exactly like the Java publisher does.
    """

    model_config = ConfigDict(populate_by_name=True)

    event_id: UUID = Field(default_factory=uuid4, alias="eventId")
    event_type: str = Field(alias="eventType")
    occurred_at: datetime = Field(default_factory=utcnow, alias="occurredAt")
    correlation_id: str | None = Field(default=None, alias="correlationId")
    payload: dict[str, Any] = Field(default_factory=dict)
    version: int = Field(default=1, alias="eventVersion")

    def payload_json(self) -> str:
        return json.dumps(
            self.payload,
            separators=(",", ":"),
            ensure_ascii=False,
            default=_json_default,
        )

    def body(self) -> bytes:
        return self.payload_json().encode("utf-8")

    def headers(self) -> dict[str, Any]:
        headers: dict[str, Any] = {
            "content_type": "application/json",
            EVENT_ID_HEADER: str(self.event_id),
            EVENT_TYPE_HEADER: self.event_type,
            EVENT_VERSION_HEADER: self.version,
        }
        if self.correlation_id:
            headers[CORRELATION_HEADER] = self.correlation_id
        return headers

    @classmethod
    def from_payload(
        cls,
        event_type: str,
        payload: Any,
        *,
        correlation_id: str | None = None,
    ) -> EventEnvelope:
        """Build an envelope from a payload model or a plain mapping.

        Payload models are serialized with camelCase aliases, which is what the
        Java side expects.
        """

        if isinstance(payload, BaseModel):
            data = payload.model_dump(by_alias=True, mode="json")
        else:
            data = dict(payload)
        return cls(
            event_type=event_type,
            correlation_id=correlation_id,
            payload=data,
        )

    @classmethod
    def from_message(
        cls,
        headers: Mapping[str, Any] | None,
        body: bytes | str,
        *,
        occurred_at: datetime | None = None,
    ) -> EventEnvelope:
        raw_headers = headers or {}
        raw_body = body.decode("utf-8") if isinstance(body, bytes) else body
        payload = json.loads(raw_body) if raw_body else {}

        event_id = _header(
            raw_headers.get(EVENT_ID_HEADER)
            or raw_headers.get(EVENT_ID_HEADER.encode())
        )
        event_type = _header(
            raw_headers.get(EVENT_TYPE_HEADER)
            or raw_headers.get(EVENT_TYPE_HEADER.encode())
        )
        version = _header(
            raw_headers.get(EVENT_VERSION_HEADER)
            or raw_headers.get(EVENT_VERSION_HEADER.encode())
        )
        correlation_id = _header(
            raw_headers.get(CORRELATION_HEADER)
            or raw_headers.get(CORRELATION_HEADER.encode())
        )

        if not event_type:
            raise ValueError("Event message has no eventType header")
        if not event_id:
            raise ValueError("Event message has no eventId header")

        return cls(
            event_id=UUID(event_id),
            event_type=event_type,
            occurred_at=occurred_at or utcnow(),
            correlation_id=correlation_id,
            payload=payload,
            version=int(version) if version else 1,
        )
