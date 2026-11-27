from datetime import UTC, datetime
from uuid import uuid4

import pytest
from common_events import (
    ALL_EVENT_PAYLOADS,
    AppointmentCreatedPayload,
    AppointmentEventTypes,
    ChatEventTypes,
    EventEnvelope,
    FileEventTypes,
    ScheduleEventTypes,
    VideoEventTypes,
    event_types_for,
    parse_payload,
)


def test_domain_payloads_serialize_as_camel_case() -> None:
    appointment_id = uuid4()
    patient_id = uuid4()
    doctor_id = uuid4()
    starts_at = datetime(2026, 5, 1, 9, 0, tzinfo=UTC)

    payload = AppointmentCreatedPayload(
        appointment_id=appointment_id,
        patient_id=patient_id,
        doctor_id=doctor_id,
        starts_at=starts_at,
        ends_at=starts_at,
    )

    body = payload.to_payload()

    assert body["appointmentId"] == str(appointment_id)
    assert body["patientId"] == str(patient_id)
    assert body["startsAt"].replace("Z", "+00:00") == starts_at.isoformat()
    assert "appointment_id" not in body


def test_envelope_from_payload_serializes_camel_case() -> None:
    event = EventEnvelope.from_payload(
        AppointmentEventTypes.CREATED,
        AppointmentCreatedPayload(
            appointment_id=uuid4(),
            patient_id=uuid4(),
            doctor_id=uuid4(),
            starts_at=datetime(2026, 5, 1, 9, 0, tzinfo=UTC),
            ends_at=datetime(2026, 5, 1, 9, 30, tzinfo=UTC),
        ),
        correlation_id="corr-1",
    )

    assert event.event_type == AppointmentEventTypes.CREATED
    assert event.correlation_id == "corr-1"
    assert "appointmentId" in event.payload


def test_registry_parses_camel_case_payload() -> None:
    appointment_id = uuid4()
    parsed = parse_payload(
        AppointmentEventTypes.CREATED,
        {
            "appointmentId": str(appointment_id),
            "patientId": str(uuid4()),
            "doctorId": str(uuid4()),
            "startsAt": "2026-05-01T09:00:00Z",
            "endsAt": "2026-05-01T09:30:00Z",
        },
    )

    assert isinstance(parsed, AppointmentCreatedPayload)
    assert parsed.appointment_id == appointment_id


def test_registry_covers_every_service_exchange() -> None:
    names = set(ALL_EVENT_PAYLOADS)

    assert AppointmentEventTypes.CREATED in names
    assert ChatEventTypes.MESSAGE_SENT in names
    assert FileEventTypes.UPLOADED in names
    assert ScheduleEventTypes.SLOT_RESERVED in names
    assert VideoEventTypes.SESSION_CREATED in names
    assert event_types_for("chat") == [
        ChatEventTypes.MESSAGE_SENT,
        ChatEventTypes.ROOM_CLOSED,
        ChatEventTypes.ROOM_CREATED,
    ]


def test_unknown_event_type_is_rejected() -> None:
    with pytest.raises(ValueError):
        parse_payload("unknown.event.v1", {})
