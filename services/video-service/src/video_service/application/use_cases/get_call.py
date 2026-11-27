"""Сценарий получения звонка."""

from __future__ import annotations

from video_service.application.dto import Actor, CallView
from video_service.application.ports import UnitOfWorkFactory
from video_service.domain.errors import CallNotFoundError, ForbiddenError
from video_service.domain.value_objects import CallId


class GetCallUseCase:
    def __init__(self, *, uow_factory: UnitOfWorkFactory) -> None:
        self._uow_factory = uow_factory

    async def execute(self, actor: Actor, call_id: CallId) -> CallView:
        async with self._uow_factory() as uow:
            call = await uow.calls.get(call_id)
            if call is None:
                raise CallNotFoundError(f"Звонок {call_id} не найден")

            if not actor.is_admin and not call.is_participant(actor.identity):
                raise ForbiddenError("Нет доступа к звонку")

            return CallView.from_entity(call)
