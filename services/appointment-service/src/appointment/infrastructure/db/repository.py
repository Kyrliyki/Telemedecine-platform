"""SQLAlchemy implementation of the appointment repository."""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Any, TypeVar, cast
from uuid import UUID

from sqlalchemy import CursorResult, Select, func, or_, select, text, update
from sqlalchemy.ext.asyncio import AsyncSession

from appointment.domain.entities import Appointment
from appointment.domain.enums import BLOCKING_STATUSES, AppointmentStatus
from appointment.domain.errors import ConcurrencyConflict
from appointment.infrastructure.db.models import AppointmentModel

_SelectT = TypeVar("_SelectT", bound=Select[Any])


def _as_utc(value: datetime | None) -> datetime | None:
    """SQLite drops tzinfo; normalise everything to aware UTC on the way out."""
    if value is None:
        return None
    if value.tzinfo is None:
        return value.replace(tzinfo=UTC)
    return value.astimezone(UTC)


def _ensure_utc(value: datetime) -> datetime:
    return value.replace(tzinfo=UTC) if value.tzinfo is None else value.astimezone(UTC)


def _to_entity(model: AppointmentModel) -> Appointment:
    return Appointment(
        id=model.id,
        patient_id=model.patient_id,
        doctor_id=model.doctor_id,
        starts_at=_as_utc(model.starts_at),  # type: ignore[arg-type]
        ends_at=_as_utc(model.ends_at),  # type: ignore[arg-type]
        status=AppointmentStatus(model.status),
        reason=model.reason,
        created_at=_as_utc(model.created_at),  # type: ignore[arg-type]
        updated_at=_as_utc(model.updated_at),  # type: ignore[arg-type]
        version=model.version,
        cancellation_reason=model.cancellation_reason,
    )


class SqlAlchemyAppointmentRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def add(self, appointment: Appointment) -> None:
        self._session.add(
            AppointmentModel(
                id=appointment.id,
                patient_id=appointment.patient_id,
                doctor_id=appointment.doctor_id,
                starts_at=appointment.starts_at,
                ends_at=appointment.ends_at,
                status=appointment.status.value,
                reason=appointment.reason,
                cancellation_reason=appointment.cancellation_reason,
                created_at=appointment.created_at,
                updated_at=appointment.updated_at,
                version=appointment.version,
            )
        )
        await self._session.flush()

    async def get(self, appointment_id: UUID) -> Appointment | None:
        model = await self._session.get(AppointmentModel, appointment_id)
        return _to_entity(model) if model is not None else None

    async def update(self, appointment: Appointment) -> None:
        """Optimistic locking: the row must still be on the previous version."""
        statement = (
            update(AppointmentModel)
            .where(
                AppointmentModel.id == appointment.id,
                AppointmentModel.version == appointment.version - 1,
            )
            .values(
                starts_at=appointment.starts_at,
                ends_at=appointment.ends_at,
                status=appointment.status.value,
                reason=appointment.reason,
                cancellation_reason=appointment.cancellation_reason,
                updated_at=appointment.updated_at,
                version=appointment.version,
            )
        )
        result = await self._session.execute(statement)
        if cast(CursorResult[Any], result).rowcount != 1:
            raise ConcurrencyConflict(
                "appointment was modified by another request, reload and try again"
            )

    async def list(
        self,
        *,
        patient_id: UUID | None = None,
        doctor_id: UUID | None = None,
        status: AppointmentStatus | None = None,
        starts_after: datetime | None = None,
        starts_before: datetime | None = None,
        limit: int = 20,
        offset: int = 0,
    ) -> list[Appointment]:
        statement = self._apply_filters(
            select(AppointmentModel),
            patient_id=patient_id,
            doctor_id=doctor_id,
            status=status,
            starts_after=starts_after,
            starts_before=starts_before,
        )
        statement = statement.order_by(AppointmentModel.starts_at.asc()).limit(limit).offset(offset)
        result = await self._session.execute(statement)
        return [_to_entity(model) for model in result.scalars().all()]

    async def count(
        self,
        *,
        patient_id: UUID | None = None,
        doctor_id: UUID | None = None,
        status: AppointmentStatus | None = None,
        starts_after: datetime | None = None,
        starts_before: datetime | None = None,
    ) -> int:
        statement = self._apply_filters(
            select(func.count()).select_from(AppointmentModel),
            patient_id=patient_id,
            doctor_id=doctor_id,
            status=status,
            starts_after=starts_after,
            starts_before=starts_before,
        )
        return int((await self._session.execute(statement)).scalar_one())

    async def find_conflict(
        self,
        *,
        patient_id: UUID,
        doctor_id: UUID,
        starts_at: datetime,
        ends_at: datetime,
        exclude_id: UUID | None = None,
    ) -> Appointment | None:
        statement = select(AppointmentModel).where(
            AppointmentModel.status.in_([status.value for status in BLOCKING_STATUSES]),
            or_(
                AppointmentModel.patient_id == patient_id,
                AppointmentModel.doctor_id == doctor_id,
            ),
            AppointmentModel.starts_at < ends_at,
            AppointmentModel.ends_at > starts_at,
        )
        if exclude_id is not None:
            statement = statement.where(AppointmentModel.id != exclude_id)
        statement = statement.limit(1)
        model = (await self._session.execute(statement)).scalar_one_or_none()
        return _to_entity(model) if model is not None else None

    async def lock_doctor(self, doctor_id: UUID) -> None:
        """Postgres advisory lock: closes the check-then-insert race for one doctor.

        On other databases (SQLite in tests) it is a no-op; use a database-level
        exclusion constraint if absolute guarantees are required there.
        """
        if self._session.get_bind().dialect.name != "postgresql":
            return
        key = doctor_id.int % (2**63)
        await self._session.execute(text("SELECT pg_advisory_xact_lock(:key)"), {"key": key})

    @staticmethod
    def _apply_filters(
        statement: _SelectT,
        *,
        patient_id: UUID | None,
        doctor_id: UUID | None,
        status: AppointmentStatus | None,
        starts_after: datetime | None,
        starts_before: datetime | None,
    ) -> _SelectT:
        if patient_id is not None:
            statement = statement.where(AppointmentModel.patient_id == patient_id)
        if doctor_id is not None:
            statement = statement.where(AppointmentModel.doctor_id == doctor_id)
        if status is not None:
            statement = statement.where(AppointmentModel.status == status.value)
        if starts_after is not None:
            statement = statement.where(AppointmentModel.starts_at >= _ensure_utc(starts_after))
        if starts_before is not None:
            statement = statement.where(AppointmentModel.starts_at < _ensure_utc(starts_before))
        return statement
