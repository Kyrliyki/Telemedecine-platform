"""Сценарий постраничного списка звонков."""

from __future__ import annotations

from video_service.application.dto import Actor, CallPage, CallView, ListCallsQuery
from video_service.application.ports import UnitOfWorkFactory


class ListCallsUseCase:
    """Администратор видит все звонки, остальные — только свои (как врач или пациент)."""

    def __init__(self, *, uow_factory: UnitOfWorkFactory) -> None:
        self._uow_factory = uow_factory

    async def execute(self, actor: Actor, query: ListCallsQuery) -> CallPage:
        async with self._uow_factory() as uow:
            if actor.is_admin:
                calls = await uow.calls.list(
                    doctor_id=query.doctor_id,
                    patient_id=query.patient_id,
                    appointment_id=query.appointment_id,
                    status=query.status,
                    limit=query.limit,
                    offset=query.offset,
                )
            else:
                calls = await uow.calls.list(
                    participant_id=actor.identity,
                    appointment_id=query.appointment_id,
                    status=query.status,
                    limit=query.limit,
                    offset=query.offset,
                )

        return CallPage(
            items=[CallView.from_entity(call) for call in calls],
            limit=query.limit,
            offset=query.offset,
        )
