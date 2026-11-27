from datetime import UTC, datetime
from uuid import uuid4

from common_events import (
    ALL_EVENT_PAYLOADS,
    DoctorRejectedPayload,
    DoctorVerifiedPayload,
    EmrEventTypes,
    EmrRecordPayload,
    UserEventTypes,
    UserProfileUpdatedPayload,
    parse_payload,
)


def test_user_payloads_serialize_as_camel_case() -> None:
    user_id = uuid4()
    payload = DoctorVerifiedPayload(
        user_id=user_id,
        email="doctor@example.test",
        specialization="Cardiology",
        verified_at=datetime(2026, 11, 24, 9, 0, tzinfo=UTC),
    )

    body = payload.to_payload()

    assert body["userId"] == str(user_id)
    assert body["specialization"] == "Cardiology"
    assert "user_id" not in body


def test_user_and_emr_events_are_registered() -> None:
    assert UserEventTypes.PROFILE_UPDATED in ALL_EVENT_PAYLOADS
    assert UserEventTypes.DOCTOR_VERIFIED in ALL_EVENT_PAYLOADS
    assert UserEventTypes.DOCTOR_REJECTED in ALL_EVENT_PAYLOADS
    assert EmrEventTypes.RECORD_CREATED in ALL_EVENT_PAYLOADS
    assert EmrEventTypes.RECORD_DELETED in ALL_EVENT_PAYLOADS


def test_registry_parses_user_and_emr_payloads() -> None:
    user_id = uuid4()
    record_id = uuid4()
    patient_id = uuid4()

    verified = parse_payload(
        UserEventTypes.DOCTOR_VERIFIED,
        {"userId": str(user_id), "email": "doctor@example.test"},
    )
    assert isinstance(verified, DoctorVerifiedPayload)
    assert verified.user_id == user_id

    rejected = parse_payload(
        UserEventTypes.DOCTOR_REJECTED,
        {"userId": str(user_id), "reason": "invalid license"},
    )
    assert isinstance(rejected, DoctorRejectedPayload)

    updated = parse_payload(
        UserEventTypes.PROFILE_UPDATED,
        {"userId": str(user_id), "roles": ["DOCTOR"]},
    )
    assert isinstance(updated, UserProfileUpdatedPayload)
    assert updated.roles == ["DOCTOR"]

    record = parse_payload(
        EmrEventTypes.RECORD_CREATED,
        {
            "recordId": str(record_id),
            "patientId": str(patient_id),
            "status": "DRAFT",
        },
    )
    assert isinstance(record, EmrRecordPayload)
    assert record.record_id == record_id
