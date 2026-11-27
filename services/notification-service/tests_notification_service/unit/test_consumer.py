"""Parser compatibility tests (canonical + legacy events)."""

from __future__ import annotations

import json
from types import SimpleNamespace
from uuid import UUID

from notification_service.infrastructure.messaging.consumer import parse_message


def message(headers: dict, body: dict | str) -> SimpleNamespace:
    raw = body if isinstance(body, str) else json.dumps(body)
    return SimpleNamespace(headers=headers, body=raw.encode("utf-8"))


def test_parses_canonical_envelope() -> None:
    event_id = UUID(int=1)
    msg = message(
        {
            "eventType": "appointment.appointment.created.v1",
            "eventId": str(event_id),
            "eventVersion": "1",
            "X-Correlation-Id": "corr-1",
        },
        {"appointmentId": str(UUID(int=2))},
    )

    envelope = parse_message(msg)

    assert envelope is not None
    assert envelope.event_type == "appointment.appointment.created.v1"
    assert envelope.event_id == event_id
    assert envelope.correlation_id == "corr-1"
    assert envelope.payload["appointmentId"] == str(UUID(int=2))


def test_parses_legacy_appointment_envelope() -> None:
    event_id = UUID(int=3)
    msg = message(
        {"type": "appointment.requested", "message_id": str(event_id)},
        {
            "event_id": str(event_id),
            "event_type": "appointment.requested",
            "occurred_at": "2026-11-24T10:00:00+00:00",
            "data": {
                "appointment_id": str(UUID(int=4)),
                "patient_id": "patient-1",
                "doctor_id": "doctor-1",
            },
        },
    )

    envelope = parse_message(msg)

    assert envelope is not None
    assert envelope.event_type == "appointment.requested"
    assert envelope.event_id == event_id
    assert envelope.payload["patient_id"] == "patient-1"


def test_parses_legacy_appointment_payload_field() -> None:
    event_id = UUID(int=9)
    msg = message(
        {"type": "appointment.requested", "message_id": str(event_id)},
        {
            "event_id": str(event_id),
            "event_type": "appointment.requested",
            "payload": {"patient_id": "patient-2"},
        },
    )

    envelope = parse_message(msg)

    assert envelope is not None
    assert envelope.payload["patient_id"] == "patient-2"


def test_parses_legacy_video_flat_payload() -> None:
    event_id = UUID(int=5)
    msg = message(
        {"type": "video.call.created", "message_id": str(event_id)},
        {
            "event_id": str(event_id),
            "event_name": "video.call.created",
            "call_id": str(UUID(int=6)),
            "appointment_id": str(UUID(int=7)),
            "room_name": "call_x",
            "doctor_id": "doctor-1",
            "patient_id": "patient-1",
        },
    )

    envelope = parse_message(msg)

    assert envelope is not None
    assert envelope.event_type == "video.call.created"
    assert envelope.payload["call_id"] == str(UUID(int=6))
    assert envelope.payload["patient_id"] == "patient-1"


def test_stable_event_id_fallback() -> None:
    body = {
        "event_name": "video.call.created",
        "call_id": str(UUID(int=8)),
        "patient_id": "patient-1",
    }
    first = parse_message(message({"type": "video.call.created"}, body))
    second = parse_message(message({"type": "video.call.created"}, body))

    assert first is not None and second is not None
    assert first.event_id == second.event_id


def test_unknown_body_is_ignored() -> None:
    assert parse_message(SimpleNamespace(headers={}, body=b"not-json")) is None
