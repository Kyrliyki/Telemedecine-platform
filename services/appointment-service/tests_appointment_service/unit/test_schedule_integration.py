"""ScheduleService integration tests for appointment use cases."""

from __future__ import annotations

from datetime import datetime, timedelta
from uuid import UUID

import pytest

from appointment.container import Container
from appointment.domain.access import Actor
from appointment.domain.enums import Role
from appointment.domain.errors import InvalidParticipant, SlotUnavailable
from appointment.infrastructure.config import Settings
from appointment.infrastructure.security import JwtAuthenticator
from tests_appointment_service.fakes import (
    DOCTOR_ID,
    PATIENT_ID,
    FakeState,
    FixedClock,
    InMemoryCache,
    InMemoryUnitOfWork,
)

START_OFFSET = timedelta(days=3)


class RecordingSchedule:
    def __init__(self) -> None:
        self.holds: list[tuple[UUID, UUID, UUID, datetime]] = []
        self.confirms: list[UUID] = []
        self.releases: list[UUID] = []
        self.hold_error: Exception | None = None

    async def hold(
        self,
        *,
        appointment_id: UUID,
        doctor_id: UUID,
        patient_id: UUID,
        starts_at: datetime,
    ) -> None:
        if self.hold_error is not None:
            raise self.hold_error
        self.holds.append((appointment_id, doctor_id, patient_id, starts_at))

    async def confirm(self, *, appointment_id: UUID) -> None:
        self.confirms.append(appointment_id)

    async def release(self, *, appointment_id: UUID) -> None:
        self.releases.append(appointment_id)

    async def aclose(self) -> None:
        return None


class FailingCommitUnitOfWork(InMemoryUnitOfWork):
    async def commit(self) -> None:
        raise RuntimeError("database commit failed")


class RecordingUsers:
    def __init__(self) -> None:
        self.patients: list[UUID] = []
        self.doctors: list[UUID] = []
        self.error: Exception | None = None

    async def require_patient(self, patient_id: UUID) -> None:
        if self.error is not None:
            raise self.error
        self.patients.append(patient_id)

    async def require_doctor(self, doctor_id: UUID) -> None:
        if self.error is not None:
            raise self.error
        self.doctors.append(doctor_id)

    async def aclose(self) -> None:
        return None


def build_container(
    state: FakeState,
    clock: FixedClock,
    cache: InMemoryCache,
    schedule: RecordingSchedule,
    users: RecordingUsers | None = None,
    *,
    failing_commit: bool = False,
) -> Container:
    settings = Settings(
        env="test",
        log_level="CRITICAL",
        jwt_secret="test-secret-key-for-unit-tests-0123456789",
        cache_enabled=False,
        broker_enabled=False,
        auto_migrate=False,
    )
    uow_class = FailingCommitUnitOfWork if failing_commit else InMemoryUnitOfWork
    return Container(
        settings=settings,
        uow_factory=lambda: uow_class(state),
        cache=cache,
        clock=clock,
        authenticator=JwtAuthenticator(settings),
        schedule=schedule,
        users=users,
    )


async def create(container: Container, clock: FixedClock):
    start = clock.now() + START_OFFSET
    return await container.create_appointment(
        actor=Actor(id=PATIENT_ID, roles=frozenset({Role.PATIENT})),
        patient_id=PATIENT_ID,
        doctor_id=DOCTOR_ID,
        starts_at=start,
        ends_at=start + timedelta(minutes=30),
        reason="schedule integration",
    )


async def test_create_holds_slot(state, clock, cache):
    schedule = RecordingSchedule()
    container = build_container(state, clock, cache, schedule)

    appointment = await create(container, clock)

    assert schedule.holds == [
        (appointment.id, DOCTOR_ID, PATIENT_ID, appointment.starts_at)
    ]


async def test_confirm_confirms_slot(state, clock, cache):
    schedule = RecordingSchedule()
    container = build_container(state, clock, cache, schedule)
    appointment = await create(container, clock)

    await container.confirm_appointment(
        actor=Actor(id=DOCTOR_ID, roles=frozenset({Role.DOCTOR})),
        appointment_id=appointment.id,
    )

    assert schedule.confirms == [appointment.id]


async def test_create_releases_hold_when_commit_fails(state, clock, cache):
    schedule = RecordingSchedule()
    container = build_container(state, clock, cache, schedule, failing_commit=True)

    with pytest.raises(RuntimeError, match="database commit failed"):
        await create(container, clock)

    assert schedule.holds
    assert schedule.releases == [schedule.holds[0][0]]


async def test_create_propagates_slot_unavailable(state, clock, cache):
    schedule = RecordingSchedule()
    schedule.hold_error = SlotUnavailable("slot is already reserved")
    container = build_container(state, clock, cache, schedule)

    with pytest.raises(SlotUnavailable, match="slot is already reserved"):
        await create(container, clock)

    assert schedule.releases == []


async def test_create_validates_participants_before_hold(state, clock, cache):
    schedule = RecordingSchedule()
    users = RecordingUsers()
    container = build_container(state, clock, cache, schedule, users)

    await create(container, clock)

    assert users.patients == [PATIENT_ID]
    assert users.doctors == [DOCTOR_ID]
    assert len(schedule.holds) == 1


async def test_create_rejects_unknown_participant_without_hold(state, clock, cache):
    schedule = RecordingSchedule()
    users = RecordingUsers()
    users.error = InvalidParticipant("Doctor account was not found")
    container = build_container(state, clock, cache, schedule, users)

    with pytest.raises(InvalidParticipant, match="Doctor account was not found"):
        await create(container, clock)

    assert schedule.holds == []
