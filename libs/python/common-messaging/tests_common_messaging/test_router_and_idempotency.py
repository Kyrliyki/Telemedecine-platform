import asyncio

import pytest
from common_events import AppointmentEventTypes, AuthEventTypes, EventEnvelope
from common_messaging import EventRouter, InMemoryProcessedEventStore, handle_once


def test_router_dispatches_exact_and_glob_handlers() -> None:
    router = EventRouter()
    received: list[str] = []

    @router.on(AuthEventTypes.USER_BLOCKED)
    async def on_blocked(envelope: EventEnvelope) -> None:
        received.append("blocked")

    @router.on("appointment.#")
    async def on_appointment(envelope: EventEnvelope) -> None:
        received.append(envelope.event_type)

    async def run() -> None:
        assert await router.dispatch(
            EventEnvelope(event_type=AuthEventTypes.USER_BLOCKED)
        )
        assert await router.dispatch(
            EventEnvelope(event_type=AppointmentEventTypes.CREATED)
        )
        assert not await router.dispatch(EventEnvelope(event_type="other.event.v1"))

    asyncio.run(run())
    assert received == ["blocked", AppointmentEventTypes.CREATED]


def test_router_can_reject_unknown_events() -> None:
    router = EventRouter(ignore_unknown=False)

    with pytest.raises(KeyError):
        asyncio.run(router.dispatch(EventEnvelope(event_type="other.event.v1")))


def test_handle_once_skips_duplicates() -> None:
    calls: list[str] = []
    store = InMemoryProcessedEventStore()
    envelope = EventEnvelope(event_type="appointment.appointment.created.v1")

    async def handler(event: EventEnvelope) -> None:
        calls.append(str(event.event_id))

    async def run() -> None:
        assert await handle_once(handler, envelope, store) is True
        assert await handle_once(handler, envelope, store) is False

    asyncio.run(run())
    assert calls == [str(envelope.event_id)]


def test_handle_once_does_not_mark_on_failure() -> None:
    store = InMemoryProcessedEventStore()
    envelope = EventEnvelope(event_type="appointment.appointment.created.v1")

    async def failing(event: EventEnvelope) -> None:
        raise RuntimeError("boom")

    async def run() -> None:
        with pytest.raises(RuntimeError):
            await handle_once(failing, envelope, store)
        assert await store.seen(envelope.event_id) is False

    asyncio.run(run())
