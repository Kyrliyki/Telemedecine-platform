"""Обработка вебхуков LiveKit: синхронизация статуса звонка."""

from __future__ import annotations

import logging

from video_service.application.dto import CallView
from video_service.application.ports import (
    Clock,
    EventPublisher,
    LiveKitRoomEvent,
    RoomEventKind,
    UnitOfWorkFactory,
)
from video_service.domain.events import CallEnded

logger = logging.getLogger(__name__)


class HandleLiveKitEventUseCase:
    """Реагирует на события комнаты: ``room_started`` и ``room_finished``.

    Это делает сервис устойчивым к обрывам связи: если браузер закрыл комнату,
    звонок в БД всё равно будет помечен завершённым.
    """

    def __init__(
        self,
        *,
        uow_factory: UnitOfWorkFactory,
        clock: Clock,
        publisher: EventPublisher,
    ) -> None:
        self._uow_factory = uow_factory
        self._clock = clock
        self._publisher = publisher

    async def execute(self, event: LiveKitRoomEvent) -> None:
        async with self._uow_factory() as uow:
            call = await uow.calls.get_by_room_name(event.room_name)
            if call is None:
                logger.warning(
                    "livekit.webhook.unknown_room",
                    extra={"room_name": event.room_name.value, "event": event.kind.value},
                )
                return

            if event.kind is RoomEventKind.ROOM_STARTED:
                if call.mark_started(self._clock.now()):
                    await uow.calls.update(call)
                    await uow.commit()
                return

            if not call.end(self._clock.now()):
                return
            await uow.calls.update(call)
            await uow.commit()
            view = CallView.from_entity(call)

        event_payload = CallEnded(
            call_id=view.id,
            appointment_id=view.appointment_id,
            room_name=view.room_name,
            doctor_id=view.doctor_id,
            patient_id=view.patient_id,
            ended_at=view.ended_at,
        )
        try:
            await self._publisher.publish(event_payload)
        except Exception:
            logger.exception("livekit.webhook.publish_failed", extra={"call_id": view.id})
        logger.info("livekit.webhook.call_ended", extra={"call_id": view.id})
