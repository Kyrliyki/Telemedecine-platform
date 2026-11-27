
from __future__ import annotations

from uuid import UUID

from chat_service.application.ports.audit_logger import AuditLogger
from chat_service.application.ports.dialog_repository import DialogRepository
from chat_service.application.ports.request_gateway import RequestGateway
from chat_service.domain.entities import Dialog, Participant
from chat_service.domain.exceptions import NotAParticipantError
from chat_service.domain.value_objects import RequestStatus


class CreateOrGetDialog:
    def __init__(
        self,
        dialogs: DialogRepository,
        gateway: RequestGateway,
        audit: AuditLogger,
    ) -> None:
        self._dialogs = dialogs
        self._gateway = gateway
        self._audit = audit

    async def execute(
        self,
        *,
        actor_id: UUID,
        request_id: str,
        participants: list[Participant],
        request_status: RequestStatus | None = None,
    ) -> tuple[Dialog, bool]:
        status = request_status or RequestStatus.OPEN
        
        info = await self._gateway.validate(request_id)
        if info is not None:
            status = info.status
            allowed_ids = {item for item in (info.patient_id, info.doctor_id) if item}
            if actor_id not in allowed_ids:
                raise NotAParticipantError("actor is not a participant of the appointment")
            if actor_id not in {participant.user_id for participant in participants}:
                raise NotAParticipantError("dialog participants must include the creator")

        dialog, created = await self._dialogs.create_idempotent(
            request_id, participants, status
        )
        if created:
            self._audit.log_event(
                "dialog.create",
                dialog_id=str(dialog.id),
                external_request_id=dialog.request_id,
            )
        return dialog, created