"""Event-to-notification mapping tests."""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from uuid import UUID

from common_events import (
    AppointmentConfirmedPayload,
    AppointmentCreatedPayload,
    AppointmentEventTypes,
    AppointmentNoShowPayload,
    AuthEventTypes,
    DoctorRejectedPayload,
    DoctorVerifiedPayload,
    EmailVerificationRequestedPayload,
    EventEnvelope,
    UserEventTypes,
    UserInvitedPayload,
    UserProfileUpdatedPayload,
    UserRegisteredPayload,
    VideoEventTypes,
    VideoSessionEndedPayload,
)

from notification_service.application.mapping import build_drafts, contact_from_event
from notification_service.domain.enums import NotificationChannel, NotificationKind

USER_ID = UUID("11111111-1111-1111-1111-111111111111")
DOCTOR_ID = UUID("22222222-2222-2222-2222-222222222222")
APPOINTMENT_ID = UUID("33333333-3333-3333-3333-333333333333")
NOW = datetime(2026, 11, 24, 10, 0, tzinfo=UTC)


def envelope(event_type: str, payload: dict) -> EventEnvelope:
    return EventEnvelope(
        event_type=event_type,
        occurred_at=NOW,
        payload=payload,
    )


def test_registered_event_creates_welcome_and_contact() -> None:
    payload = UserRegisteredPayload(
        user_id=USER_ID,
        email="patient@example.test",
        roles=["PATIENT"],
        email_verified=False,
        source="password",
    )
    event = EventEnvelope.from_payload(AuthEventTypes.USER_REGISTERED, payload)

    drafts = build_drafts(
        event,
        frontend_url="http://frontend.test",
        email_enabled=True,
        in_app_enabled=True,
    )

    assert {draft.channel for draft in drafts} == {
        NotificationChannel.IN_APP,
        NotificationChannel.EMAIL,
    }
    assert all(draft.user_id == str(USER_ID) for draft in drafts)
    assert len({draft.dedup_key for draft in drafts}) == 2

    contact = contact_from_event(event)
    assert contact is not None
    assert contact.email == "patient@example.test"


def test_email_verification_contains_token_and_is_email_only() -> None:
    payload = EmailVerificationRequestedPayload(
        user_id=USER_ID,
        email="patient@example.test",
        expires_at=NOW + timedelta(hours=1),
        raw_token="raw-token-value",
    )
    event = EventEnvelope.from_payload(AuthEventTypes.USER_EMAIL_VERIFICATION_REQUESTED, payload)

    drafts = build_drafts(
        event,
        frontend_url="http://frontend.test",
        email_enabled=True,
        in_app_enabled=True,
    )

    assert len(drafts) == 1
    draft = drafts[0]
    assert draft.channel is NotificationChannel.EMAIL
    assert draft.kind == NotificationKind.AUTH_EMAIL_VERIFICATION.value
    assert "raw-token-value" in draft.body
    assert "raw-token-value" not in draft.payload.values()


def test_legacy_appointment_event_notifies_patient_and_doctor() -> None:
    event = envelope(
        "appointment.requested",
        {
            "appointment_id": str(APPOINTMENT_ID),
            "patient_id": str(USER_ID),
            "doctor_id": str(DOCTOR_ID),
            "starts_at": "2026-11-25T10:00:00+00:00",
            "ends_at": "2026-11-25T10:30:00+00:00",
            "status": "requested",
            "reason": "консультация",
        },
    )

    drafts = build_drafts(
        event,
        frontend_url="http://frontend.test",
        email_enabled=True,
        in_app_enabled=True,
    )

    assert len(drafts) == 4
    assert {draft.user_id for draft in drafts} == {str(USER_ID), str(DOCTOR_ID)}
    assert {draft.channel for draft in drafts} == {
        NotificationChannel.IN_APP,
        NotificationChannel.EMAIL,
    }
    assert all(draft.kind == NotificationKind.APPOINTMENT_CREATED.value for draft in drafts)
    assert any("25.11.2026" in draft.body for draft in drafts)


def test_canonical_appointment_event_is_supported() -> None:
    payload = AppointmentCreatedPayload(
        appointment_id=APPOINTMENT_ID,
        patient_id=USER_ID,
        doctor_id=DOCTOR_ID,
        starts_at=NOW,
        ends_at=NOW + timedelta(minutes=30),
    )
    event = EventEnvelope.from_payload(AppointmentEventTypes.CREATED, payload)

    drafts = build_drafts(
        event,
        frontend_url="http://frontend.test",
        email_enabled=False,
        in_app_enabled=True,
    )

    assert len(drafts) == 2
    assert all(draft.channel is NotificationChannel.IN_APP for draft in drafts)


def test_canonical_appointment_confirmed_event_notifies_both_sides() -> None:
    payload = AppointmentConfirmedPayload(
        appointment_id=APPOINTMENT_ID,
        patient_id=USER_ID,
        doctor_id=DOCTOR_ID,
        starts_at=NOW,
        ends_at=NOW + timedelta(minutes=30),
    )
    event = EventEnvelope.from_payload(AppointmentEventTypes.CONFIRMED, payload)

    drafts = build_drafts(
        event,
        frontend_url="http://frontend.test",
        email_enabled=False,
        in_app_enabled=True,
    )

    assert len(drafts) == 2
    assert all(draft.kind == NotificationKind.APPOINTMENT_CONFIRMED.value for draft in drafts)


def test_canonical_appointment_no_show_event_notifies_both_sides() -> None:
    payload = AppointmentNoShowPayload(
        appointment_id=APPOINTMENT_ID,
        patient_id=USER_ID,
        doctor_id=DOCTOR_ID,
        starts_at=NOW,
        ends_at=NOW + timedelta(minutes=30),
    )
    event = EventEnvelope.from_payload(AppointmentEventTypes.NO_SHOW, payload)

    drafts = build_drafts(
        event,
        frontend_url="http://frontend.test",
        email_enabled=False,
        in_app_enabled=True,
    )

    assert len(drafts) == 2
    assert all(draft.kind == NotificationKind.APPOINTMENT_NO_SHOW.value for draft in drafts)


def test_invitation_email_contains_acceptance_token() -> None:
    payload = UserInvitedPayload(
        user_id=USER_ID,
        email="patient@example.test",
        roles=["PATIENT"],
        expires_at=NOW + timedelta(days=2),
        raw_token="invite-token",
    )
    event = EventEnvelope.from_payload(AuthEventTypes.USER_INVITED, payload)

    drafts = build_drafts(
        event,
        frontend_url="http://frontend.test",
        email_enabled=True,
        in_app_enabled=True,
    )

    assert len(drafts) == 2
    assert any("invite-token" in draft.body for draft in drafts)
    assert any("/accept-invitation" in draft.body for draft in drafts)


def test_legacy_video_ended_notifies_patient() -> None:
    event = envelope(
        "video.call.ended",
        {
            "call_id": str(APPOINTMENT_ID),
            "appointment_id": str(APPOINTMENT_ID),
            "room_name": "call_1",
            "doctor_id": str(DOCTOR_ID),
            "patient_id": str(USER_ID),
            "ended_at": NOW.isoformat(),
        },
    )

    drafts = build_drafts(
        event,
        frontend_url="http://frontend.test",
        email_enabled=False,
        in_app_enabled=True,
    )

    assert len(drafts) == 1
    assert drafts[0].user_id == str(USER_ID)
    assert drafts[0].kind == NotificationKind.VIDEO_CALL_ENDED.value


def test_canonical_video_ended_notifies_patient() -> None:
    payload = VideoSessionEndedPayload(
        session_id=APPOINTMENT_ID,
        room_name="call_1",
        ended_at=NOW,
        duration_seconds=60,
        patient_id=USER_ID,
    )
    event = EventEnvelope.from_payload(VideoEventTypes.SESSION_ENDED, payload)

    drafts = build_drafts(
        event,
        frontend_url="http://frontend.test",
        email_enabled=False,
        in_app_enabled=True,
    )

    assert len(drafts) == 1
    assert drafts[0].user_id == str(USER_ID)


def test_doctor_verified_event_notifies_doctor() -> None:
    payload = DoctorVerifiedPayload(
        user_id=DOCTOR_ID,
        email="doctor@example.test",
        specialization="Cardiology",
    )
    event = EventEnvelope.from_payload(UserEventTypes.DOCTOR_VERIFIED, payload)

    drafts = build_drafts(
        event,
        frontend_url="http://frontend.test",
        email_enabled=True,
        in_app_enabled=True,
    )

    assert len(drafts) == 2
    assert {draft.channel for draft in drafts} == {
        NotificationChannel.IN_APP,
        NotificationChannel.EMAIL,
    }
    assert all(draft.kind == NotificationKind.USER_DOCTOR_VERIFIED.value for draft in drafts)
    assert all(draft.user_id == str(DOCTOR_ID) for draft in drafts)


def test_doctor_rejected_event_notifies_doctor() -> None:
    payload = DoctorRejectedPayload(
        user_id=DOCTOR_ID,
        email="doctor@example.test",
        reason="invalid license",
    )
    event = EventEnvelope.from_payload(UserEventTypes.DOCTOR_REJECTED, payload)

    drafts = build_drafts(
        event,
        frontend_url="http://frontend.test",
        email_enabled=True,
        in_app_enabled=True,
    )

    assert len(drafts) == 2
    assert all(draft.kind == NotificationKind.USER_DOCTOR_REJECTED.value for draft in drafts)
    assert any("invalid license" in draft.body for draft in drafts)


def test_doctor_verified_without_email_is_in_app_only() -> None:
    event = EventEnvelope.from_payload(
        UserEventTypes.DOCTOR_VERIFIED,
        DoctorVerifiedPayload(user_id=DOCTOR_ID),
    )

    drafts = build_drafts(
        event,
        frontend_url="http://frontend.test",
        email_enabled=True,
        in_app_enabled=True,
    )

    assert len(drafts) == 1
    assert drafts[0].channel is NotificationChannel.IN_APP


def test_profile_updated_event_is_in_app_only() -> None:
    event = EventEnvelope.from_payload(
        UserEventTypes.PROFILE_UPDATED,
        UserProfileUpdatedPayload(user_id=USER_ID, roles=["PATIENT"]),
    )

    drafts = build_drafts(
        event,
        frontend_url="http://frontend.test",
        email_enabled=True,
        in_app_enabled=True,
    )

    assert len(drafts) == 1
    assert drafts[0].kind == NotificationKind.USER_PROFILE_UPDATED.value
    assert drafts[0].channel is NotificationChannel.IN_APP


def test_legacy_video_event_notifies_patient_only() -> None:
    event = envelope(
        "video.call.created",
        {
            "event_id": str(UUID(int=77)),
            "event_name": "video.call.created",
            "call_id": str(UUID(int=78)),
            "appointment_id": str(APPOINTMENT_ID),
            "room_name": "call_abc",
            "doctor_id": str(DOCTOR_ID),
            "patient_id": str(USER_ID),
        },
    )

    drafts = build_drafts(
        event,
        frontend_url="http://frontend.test",
        email_enabled=True,
        in_app_enabled=True,
    )

    assert len(drafts) == 2
    assert {draft.user_id for draft in drafts} == {str(USER_ID)}
    assert {draft.channel for draft in drafts} == {
        NotificationChannel.IN_APP,
        NotificationChannel.EMAIL,
    }
    assert {draft.kind for draft in drafts} == {NotificationKind.VIDEO_CALL_STARTED.value}
