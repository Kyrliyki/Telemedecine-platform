"""SQLAlchemy-реализация CallRepository."""

from __future__ import annotations

import uuid

from sqlalchemy import or_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from video_service.domain.entities import Call
from video_service.domain.enums import CallStatus
from video_service.domain.errors import CallAlreadyExistsError, CallNotFoundError
from video_service.domain.value_objects import CallId, RoomName
from video_service.infrastructure.persistence.postgres.mappers import to_entity, to_model
from video_service.infrastructure.persistence.postgres.models import CallModel


class SqlAlchemyCallRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def get(self, call_id: CallId) -> Call | None:
        model = await self._session.get(CallModel, call_id.value)
        return to_entity(model) if model else None

    async def get_current_by_appointment(self, appointment_id: uuid.UUID) -> Call | None:
        """Незавершённый звонок по приёму (не более одного благодаря partial unique index)."""
        result = await self._session.execute(
            select(CallModel)
            .where(
                CallModel.appointment_id == appointment_id,
                CallModel.status != CallStatus.ENDED.value,
            )
            .order_by(CallModel.created_at.desc())
            .limit(1),
        )
        model = result.scalar_one_or_none()
        return to_entity(model) if model else None

    async def get_by_room_name(self, room_name: RoomName) -> Call | None:
        result = await self._session.execute(
            select(CallModel).where(CallModel.room_name == room_name.value),
        )
        model = result.scalar_one_or_none()
        return to_entity(model) if model else None

    async def list(
        self,
        *,
        participant_id: str | None = None,
        doctor_id: str | None = None,
        patient_id: str | None = None,
        appointment_id: uuid.UUID | None = None,
        status: CallStatus | None = None,
        limit: int = 50,
        offset: int = 0,
    ) -> list[Call]:
        stmt = select(CallModel)

        if participant_id is not None:
            stmt = stmt.where(
                or_(
                    CallModel.doctor_id == participant_id,
                    CallModel.patient_id == participant_id,
                ),
            )
        if doctor_id is not None:
            stmt = stmt.where(CallModel.doctor_id == doctor_id)
        if patient_id is not None:
            stmt = stmt.where(CallModel.patient_id == patient_id)
        if appointment_id is not None:
            stmt = stmt.where(CallModel.appointment_id == appointment_id)
        if status is not None:
            stmt = stmt.where(CallModel.status == status.value)

        stmt = stmt.order_by(CallModel.created_at.desc()).limit(limit).offset(offset)
        result = await self._session.execute(stmt)
        return [to_entity(model) for model in result.scalars().all()]

    async def add(self, call: Call) -> None:
        self._session.add(to_model(call))
        try:
            await self._session.flush()
        except IntegrityError as exc:
            raise CallAlreadyExistsError(
                f"Звонок для приёма {call.appointment_id} уже существует",
            ) from exc

    async def update(self, call: Call) -> None:
        model = await self._session.get(CallModel, call.id.value)
        if model is None:
            raise CallNotFoundError(f"Звонок {call.id} не найден")

        model.appointment_id = call.appointment_id
        model.room_name = call.room_name.value
        model.doctor_id = call.doctor_id
        model.patient_id = call.patient_id
        model.status = call.status.value
        model.max_participants = call.max_participants
        model.created_at = call.created_at
        model.started_at = call.started_at
        model.ended_at = call.ended_at
