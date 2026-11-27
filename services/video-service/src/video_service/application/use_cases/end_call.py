"""Сценарий завершения звонка."""

from __future__ import annotations

import logging
from datetime import datetime

from video_service.application.dto import Actor, CallView
from video_service.application.ports import (
    Clock,
    EventPublisher,
    LiveKitGateway,
    UnitOfWorkFactory,
)
from video_service.domain.errors import CallNotFoundError
from video_service.domain.events import CallEnded
from video_service.domain.value_objects import CallId, RoomName

logger = logging.getLogger(__name__)

class EndCallUseCase:
    """Завершает звонок и (best-effort) удаляет комнату из LiveKit.

    Идемпотентен: повторный вызов возвращает уже завершённый звонок.
    """

    def __init__(
        self,
        *,
        uow_factory: UnitOfWorkFactory,
        livekit: LiveKitGateway,
        clock: Clock,
        publisher: EventPublisher,
    ) -> None:
        self._uow_factory = uow_factory
        self._livekit = livekit
        self._clock = clock
        self._publisher = publisher

    async def execute(self, actor: Actor, call_id: CallId) -> CallView:
        room_name: RoomName | None = None
        ended_at: datetime | None = None
        changed = False

        async with self._uow_factory() as uow:
            call = await uow.calls.get(call_id)
            if call is None:
                raise CallNotFoundError(f"Звонок {call_id} не найден")

            call.ensure_can_manage(actor.identity, actor.roles)

            changed = call.end(self._clock.now())
            if changed:
                await uow.calls.update(call)
                await uow.commit()
                room_name = call.room_name
                ended_at = call.ended_at

            view = CallView.from_entity(call)

        if not changed or room_name is None:
            return view

        try:
            await self._livekit.delete_room(room_name=room_name)
        except Exception:
            
            logger.exception(
                "call.end.room_delete_failed",
                extra={"call_id": view.id, "room_name": room_name.value},
            )

        await self._publish_ended(view, ended_at)
        logger.info("call.ended", extra={"call_id": view.id, "room_name": room_name.value})
        return view

    async def _publish_ended(self, call: CallView, ended_at: datetime | None) -> None:
        event = CallEnded(
            call_id=call.id,
            appointment_id=call.appointment_id,
            room_name=call.room_name,
            doctor_id=call.doctor_id,
            patient_id=call.patient_id,
            ended_at=ended_at,
        )
        try:
            await self._publisher.publish(event)
        except Exception:
            logger.exception("call.ended.event_publish_failed", extra={"call_id": call.id})
