"""Integration tests for the persistence adapters (real PostgreSQL via Testcontainers)."""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from uuid import uuid4

import pytest
from sqlalchemy import inspect, select
from sqlalchemy.ext.asyncio import create_async_engine
from sqlalchemy.pool import NullPool

from appointment.domain.entities import Appointment
from appointment.domain.enums import AppointmentStatus
from appointment.domain.errors import ConcurrencyConflict
from appointment.domain.events import appointment_requested
from appointment.infrastructure.db.models import OutboxMessageModel
from appointment.infrastructure.db.uow import SqlAlchemyUnitOfWork
from appointment.infrastructure.outbox_relay import OutboxRelay
from tests_appointment_service.fakes import RecordingPublisher

pytestmark = pytest.mark.integration


def make_appointment(*, now: datetime, offset_hours: int = 0, **kwargs) -> Appointment:
    start = now + timedelta(days=2, hours=offset_hours)
    return Appointment.schedule(
        patient_id=kwargs.pop("patient_id", uuid4()),
        doctor_id=kwargs.pop("doctor_id", uuid4()),
        starts_at=start,
        ends_at=start + timedelta(minutes=30),
        reason=kwargs.pop("reason", "Integration test"),
        now=now,
        **kwargs,
    )


async def test_add_read_and_outbox(session_factory):
    now = datetime.now(UTC)
    appointment = make_appointment(now=now)

    uow = SqlAlchemyUnitOfWork(session_factory)
    async with uow:
        await uow.appointments.add(appointment)
        await uow.outbox.add(appointment_requested(appointment, now=now))
        await uow.commit()

    async with uow:
        loaded = await uow.appointments.get(appointment.id)
        assert loaded is not None
        assert loaded.status is AppointmentStatus.REQUESTED
        assert loaded.starts_at.tzinfo is not None
        assert loaded.reason == "Integration test"

        items = await uow.appointments.list(patient_id=appointment.patient_id)
        assert [item.id for item in items] == [appointment.id]
        assert await uow.appointments.count(doctor_id=appointment.doctor_id) == 1

    async with session_factory() as session:
        rows = (await session.execute(select(OutboxMessageModel))).scalars().all()
        assert len(rows) == 1
        assert rows[0].published_at is None


async def test_overlap_query_ignores_cancelled_appointments(session_factory):
    now = datetime.now(UTC)
    appointment = make_appointment(now=now)
    patient_id, doctor_id = appointment.patient_id, appointment.doctor_id

    uow = SqlAlchemyUnitOfWork(session_factory)
    async with uow:
        await uow.appointments.add(appointment)
        await uow.commit()

    async with uow:
        conflict = await uow.appointments.find_conflict(
            patient_id=patient_id,
            doctor_id=doctor_id,
            starts_at=appointment.starts_at + timedelta(minutes=15),
            ends_at=appointment.ends_at + timedelta(minutes=15),
        )
        assert conflict is not None
        assert conflict.id == appointment.id

    async with uow:
        loaded = await uow.appointments.get(appointment.id)
        assert loaded is not None
        loaded.cancel(reason="Changed plans", now=now)
        await uow.appointments.update(loaded)
        await uow.commit()

    async with uow:
        conflict = await uow.appointments.find_conflict(
            patient_id=patient_id,
            doctor_id=doctor_id,
            starts_at=appointment.starts_at,
            ends_at=appointment.ends_at,
        )
        assert conflict is None


async def test_optimistic_lock_detects_stale_update(session_factory):
    now = datetime.now(UTC)
    appointment = make_appointment(now=now)

    uow = SqlAlchemyUnitOfWork(session_factory)
    async with uow:
        await uow.appointments.add(appointment)
        await uow.commit()

    async with uow:
        first_copy = await uow.appointments.get(appointment.id)
        second_copy = await uow.appointments.get(appointment.id)
        assert first_copy is not None
        assert second_copy is not None

    async with uow:
        first_copy.confirm(now=now)
        await uow.appointments.update(first_copy)
        await uow.commit()

    async with uow:
        second_copy.confirm(now=now)
        with pytest.raises(ConcurrencyConflict):
            await uow.appointments.update(second_copy)


async def test_list_pagination_and_filters(session_factory):
    now = datetime.now(UTC)
    patient_id, doctor_id = uuid4(), uuid4()

    uow = SqlAlchemyUnitOfWork(session_factory)
    async with uow:
        for index in range(3):
            await uow.appointments.add(
                make_appointment(
                    now=now,
                    offset_hours=index,
                    patient_id=patient_id,
                    doctor_id=doctor_id,
                    reason=f"Visit {index}",
                )
            )
        await uow.commit()

    async with uow:
        page = await uow.appointments.list(limit=2, offset=0)
        tail = await uow.appointments.list(limit=2, offset=2)
        total = await uow.appointments.count()
        assert len(page) == 2
        assert len(tail) == 1
        assert total == 3
        assert page[0].starts_at < page[1].starts_at < tail[0].starts_at

        requested = await uow.appointments.count(status=AppointmentStatus.REQUESTED)
        assert requested == 3


async def test_migrations_create_schema(migrated_database_url):
    engine = create_async_engine(migrated_database_url, poolclass=NullPool)
    async with engine.connect() as connection:
        tables = await connection.run_sync(
            lambda sync_connection: set(inspect(sync_connection).get_table_names())
        )
    await engine.dispose()

    assert {"appointments", "outbox_messages", "alembic_version"} <= tables


async def test_outbox_relay_publishes_and_marks(session_factory):
    now = datetime.now(UTC)
    appointment = make_appointment(now=now)
    event = appointment_requested(appointment, now=now)

    uow = SqlAlchemyUnitOfWork(session_factory)
    async with uow:
        await uow.appointments.add(appointment)
        await uow.outbox.add(event)
        await uow.commit()

    publisher = RecordingPublisher()
    relay = OutboxRelay(session_factory, publisher, batch_size=10)

    assert await relay.drain_once() == 1
    assert [published.event_id for published in publisher.events] == [event.event_id]
    assert await relay.drain_once() == 0

    async with session_factory() as session:
        row = await session.get(OutboxMessageModel, event.event_id)
        assert row is not None
        assert row.published_at is not None
        assert row.attempts == 1
