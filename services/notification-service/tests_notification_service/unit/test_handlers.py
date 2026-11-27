"""Router registration for Java user/EMR events."""

from __future__ import annotations

from common_events import EmrEventTypes, EventEnvelope, UserEventTypes
from common_messaging import EventRouter

from notification_service.infrastructure.messaging.handlers import register_handlers


class RecordingIngest:
    def __init__(self) -> None:
        self.events: list[EventEnvelope] = []

    async def __call__(self, envelope: EventEnvelope) -> None:
        self.events.append(envelope)


def test_user_events_are_registered() -> None:
    router = EventRouter()
    register_handlers(router, RecordingIngest())

    assert router.handles(UserEventTypes.PROFILE_UPDATED)
    assert router.handles(UserEventTypes.DOCTOR_VERIFIED)
    assert router.handles(UserEventTypes.DOCTOR_REJECTED)


def test_unknown_emr_event_is_ignored() -> None:
    router = EventRouter()
    register_handlers(router, RecordingIngest())

    assert router.handles(EmrEventTypes.RECORD_CREATED) is False


async def test_registered_user_event_reaches_ingest() -> None:
    router = EventRouter()
    ingest = RecordingIngest()
    register_handlers(router, ingest)
    envelope = EventEnvelope(
        event_type=UserEventTypes.DOCTOR_REJECTED,
        payload={"userId": "u-1", "reason": "invalid license"},
    )

    handled = await router.dispatch(envelope)

    assert handled is True
    assert ingest.events == [envelope]
