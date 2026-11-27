"""Use cases: orchestration of the domain, the unit of work, cache and events.

Each use case is a small callable object. Dependencies are injected by the
container, which makes them trivial to test with in-memory fakes.
"""

from __future__ import annotations

import logging
from collections.abc import Callable
from dataclasses import replace
from datetime import datetime
from uuid import UUID

from appointment.application.dto import (
    ListAppointmentsQuery,
    appointment_from_dict,
    appointment_to_dict,
    cache_key,
)
from appointment.application.ports import (
    Cache,
    Clock,
    ScheduleGateway,
    UserDirectoryGateway,
)
from appointment.domain.access import Actor
from appointment.domain.entities import Appointment
from appointment.domain.errors import (
    AppointmentNotFound,
    OverlappingAppointment,
    PermissionDenied,
    ValidationError,
)
from appointment.domain.events import (
    appointment_cancelled,
    appointment_completed,
    appointment_confirmed,
    appointment_no_show,
    appointment_requested,
    appointment_rescheduled,
)
from appointment.domain.ports import UnitOfWork

logger = logging.getLogger(__name__)

UoWFactory = Callable[[], UnitOfWork]

def _conflict_message(conflict: Appointment, *, patient_id: UUID, doctor_id: UUID) -> str:
    patient_busy = conflict.patient_id == patient_id
    doctor_busy = conflict.doctor_id == doctor_id
    if patient_busy and doctor_busy:
        return "this patient already has an appointment in the selected time window"
    if patient_busy:
        return "the patient already has another appointment at this time"
    return "the doctor is already booked for this time window"

class _BaseUseCase:
    def __init__(
        self,
        uow_factory: UoWFactory,
        cache: Cache,
        clock: Clock,
        schedule: ScheduleGateway | None = None,
        users: UserDirectoryGateway | None = None,
    ) -> None:
        self._uow_factory = uow_factory
        self._cache = cache
        self._clock = clock
        self._schedule = schedule
        self._users = users

    async def _validate_participants(self, *, patient_id: UUID, doctor_id: UUID) -> None:
        if self._users is None:
            return
        await self._users.require_patient(patient_id)
        await self._users.require_doctor(doctor_id)

    async def _load(self, uow: UnitOfWork, appointment_id: UUID) -> Appointment:
        appointment = await uow.appointments.get(appointment_id)
        if appointment is None:
            raise AppointmentNotFound(f"appointment {appointment_id} was not found")
        return appointment

    async def _release_slot_quietly(self, appointment_id: UUID) -> None:
        if self._schedule is None:
            return
        try:
            await self._schedule.release(appointment_id=appointment_id)
        except Exception:
            logger.warning(
                "schedule_release_failed appointment_id=%s",
                appointment_id,
                exc_info=True,
            )

    async def _invalidate_cache(self, appointment_id: UUID) -> None:
        await self._cache.delete(cache_key(appointment_id))

class CreateAppointment(_BaseUseCase):
    """Book a new consultation slot."""

    async def __call__(
        self,
        *,
        actor: Actor,
        patient_id: UUID,
        doctor_id: UUID,
        starts_at: datetime,
        ends_at: datetime,
        reason: str,
    ) -> Appointment:
        mode = actor.booking_mode(patient_id=patient_id, doctor_id=doctor_id)
        await self._validate_participants(patient_id=patient_id, doctor_id=doctor_id)
        now = self._clock.now()
        actor.ensure_can_schedule(starts_at=starts_at, now=now, mode=mode)
        appointment = Appointment.schedule(
            patient_id=patient_id,
            doctor_id=doctor_id,
            starts_at=starts_at,
            ends_at=ends_at,
            reason=reason,
            now=now,
        )

        slot_held = False
        try:
            async with self._uow_factory() as uow:
                await uow.appointments.lock_doctor(doctor_id)
                conflict = await uow.appointments.find_conflict(
                    patient_id=patient_id,
                    doctor_id=doctor_id,
                    starts_at=appointment.starts_at,
                    ends_at=appointment.ends_at,
                )
                if conflict is not None:
                    raise OverlappingAppointment(
                        _conflict_message(conflict, patient_id=patient_id, doctor_id=doctor_id)
                    )
                if self._schedule is not None:
                    await self._schedule.hold(
                        appointment_id=appointment.id,
                        doctor_id=doctor_id,
                        patient_id=patient_id,
                        starts_at=appointment.starts_at,
                    )
                    slot_held = True
                await uow.appointments.add(appointment)
                await uow.outbox.add(appointment_requested(appointment, now=now))
                await uow.commit()
        except Exception:
            if slot_held:
                await self._release_slot_quietly(appointment.id)
            raise

        return appointment

class GetAppointment(_BaseUseCase):
    """Read one appointment (cache-aside through Redis)."""

    async def __call__(self, *, actor: Actor, appointment_id: UUID) -> Appointment:
        key = cache_key(appointment_id)
        cached = await self._cache.get_json(key)
        if isinstance(cached, dict):
            try:
                appointment = appointment_from_dict(cached)
            except (KeyError, TypeError, ValueError):
                await self._cache.delete(key)
            else:
                actor.ensure_can_view(appointment)
                return appointment

        async with self._uow_factory() as uow:
            appointment = await self._load(uow, appointment_id)

        actor.ensure_can_view(appointment)
        await self._cache.set_json(key, appointment_to_dict(appointment))
        return appointment

class ListAppointments(_BaseUseCase):
    """List appointments visible to the caller with filters and pagination."""

    async def __call__(
        self, *, actor: Actor, query: ListAppointmentsQuery
    ) -> tuple[list[Appointment], int]:
        actor.ensure_can_filter(patient_id=query.patient_id, doctor_id=query.doctor_id)

        view = query.view.lower() if query.view is not None else None
        if view is not None:
            if view == "patient":
                if not actor.is_patient:
                    raise PermissionDenied("the patient view requires the PATIENT role")
                query = replace(query, patient_id=actor.id, doctor_id=None)
            elif view == "doctor":
                if not actor.is_doctor:
                    raise PermissionDenied("the doctor view requires the DOCTOR role")
                query = replace(query, doctor_id=actor.id, patient_id=None)
            else:
                raise ValidationError("view must be 'patient' or 'doctor'")
        elif actor.is_admin:
            pass
        elif actor.is_patient and not actor.is_doctor:
            query = replace(query, patient_id=actor.id, doctor_id=None)
        elif actor.is_doctor and not actor.is_patient:
            query = replace(query, doctor_id=actor.id, patient_id=None)
        else:
            
            
            query = replace(query, doctor_id=actor.id, patient_id=None)

        async with self._uow_factory() as uow:
            items = await uow.appointments.list(
                patient_id=query.patient_id,
                doctor_id=query.doctor_id,
                status=query.status,
                starts_after=query.starts_after,
                starts_before=query.starts_before,
                limit=query.limit,
                offset=query.offset,
            )
            total = await uow.appointments.count(
                patient_id=query.patient_id,
                doctor_id=query.doctor_id,
                status=query.status,
                starts_after=query.starts_after,
                starts_before=query.starts_before,
            )
        return items, total

class ConfirmAppointment(_BaseUseCase):
    """Doctor accepts a requested appointment."""

    async def __call__(self, *, actor: Actor, appointment_id: UUID) -> Appointment:
        now = self._clock.now()
        slot_confirmed = False
        try:
            async with self._uow_factory() as uow:
                appointment = await self._load(uow, appointment_id)
                actor.ensure_can_manage_clinically(appointment)
                appointment.confirm(now=now)
                if self._schedule is not None:
                    await self._schedule.confirm(appointment_id=appointment_id)
                    slot_confirmed = True
                await uow.appointments.update(appointment)
                await uow.outbox.add(appointment_confirmed(appointment, now=now))
                await uow.commit()
        except Exception:
            if slot_confirmed:
                await self._release_slot_quietly(appointment_id)
            raise
        await self._invalidate_cache(appointment_id)
        return appointment

class RescheduleAppointment(_BaseUseCase):
    """Move an active appointment to a new time window."""

    async def __call__(
        self,
        *,
        actor: Actor,
        appointment_id: UUID,
        starts_at: datetime,
        ends_at: datetime,
    ) -> Appointment:
        now = self._clock.now()
        async with self._uow_factory() as uow:
            appointment = await self._load(uow, appointment_id)
            actor.ensure_can_manage(appointment)
            mode = actor.mode_for(appointment)
            actor.ensure_can_schedule(starts_at=starts_at, now=now, mode=mode)
            await self._validate_participants(
                patient_id=appointment.patient_id,
                doctor_id=appointment.doctor_id,
            )
            previous_starts_at, previous_ends_at = appointment.starts_at, appointment.ends_at
            appointment.reschedule(starts_at=starts_at, ends_at=ends_at, now=now)

            await uow.appointments.lock_doctor(appointment.doctor_id)
            conflict = await uow.appointments.find_conflict(
                patient_id=appointment.patient_id,
                doctor_id=appointment.doctor_id,
                starts_at=appointment.starts_at,
                ends_at=appointment.ends_at,
                exclude_id=appointment.id,
            )
            if conflict is not None:
                raise OverlappingAppointment(
                    _conflict_message(
                        conflict,
                        patient_id=appointment.patient_id,
                        doctor_id=appointment.doctor_id,
                    )
                )

            await uow.appointments.update(appointment)
            await uow.outbox.add(
                appointment_rescheduled(
                    appointment,
                    previous_starts_at=previous_starts_at,
                    previous_ends_at=previous_ends_at,
                    now=now,
                )
            )
            await uow.commit()
        await self._invalidate_cache(appointment_id)
        await self._release_slot_quietly(appointment_id)
        return appointment

class CancelAppointment(_BaseUseCase):
    """Cancel a future appointment."""

    async def __call__(self, *, actor: Actor, appointment_id: UUID, reason: str) -> Appointment:
        now = self._clock.now()
        async with self._uow_factory() as uow:
            appointment = await self._load(uow, appointment_id)
            actor.ensure_can_manage(appointment)
            appointment.cancel(reason=reason, now=now)
            await uow.appointments.update(appointment)
            await uow.outbox.add(appointment_cancelled(appointment, now=now))
            await uow.commit()
        await self._invalidate_cache(appointment_id)
        await self._release_slot_quietly(appointment_id)
        return appointment

class CompleteAppointment(_BaseUseCase):
    """Mark a confirmed appointment as completed."""

    async def __call__(self, *, actor: Actor, appointment_id: UUID) -> Appointment:
        now = self._clock.now()
        async with self._uow_factory() as uow:
            appointment = await self._load(uow, appointment_id)
            actor.ensure_can_manage_clinically(appointment)
            appointment.complete(now=now, force=actor.is_admin)
            await uow.appointments.update(appointment)
            await uow.outbox.add(appointment_completed(appointment, now=now))
            await uow.commit()
        await self._invalidate_cache(appointment_id)
        return appointment

class MarkNoShow(_BaseUseCase):
    """Doctor marks that the patient did not join."""

    async def __call__(self, *, actor: Actor, appointment_id: UUID) -> Appointment:
        now = self._clock.now()
        async with self._uow_factory() as uow:
            appointment = await self._load(uow, appointment_id)
            actor.ensure_can_manage_clinically(appointment)
            appointment.mark_no_show(now=now)
            await uow.appointments.update(appointment)
            await uow.outbox.add(appointment_no_show(appointment, now=now))
            await uow.commit()
        await self._invalidate_cache(appointment_id)
        return appointment
