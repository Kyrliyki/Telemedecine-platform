from uuid import UUID

from chat_service.application.ports.audit_logger import AuditLogger
from chat_service.application.ports.dialog_repository import DialogRepository
from chat_service.application.ports.publisher import EventPublisher
from chat_service.application.ports.state_repository import DialogStateRepository
from chat_service.domain.entities import Dialog, Participant
from chat_service.domain.exceptions import DialogNotFoundError, NotAParticipantError
from chat_service.domain.value_objects import RequestStatus


class UpdateDialog:
    def __init__(
        self,
        dialogs: DialogRepository,
        states: DialogStateRepository,
        publisher: EventPublisher,
        audit: AuditLogger,
    ) -> None:
        self._dialogs = dialogs
        self._states = states
        self._publisher = publisher
        self._audit = audit

    async def execute(
        self,
        *,
        dialog_id: UUID,
        actor_id: UUID,
        request_status: RequestStatus | None = None,
        archived: bool | None = None,
        participants: list[Participant] | None = None,
    ) -> Dialog:
        dialog = await self._dialogs.get(dialog_id)
        if dialog is None:
            raise DialogNotFoundError()
        if not dialog.is_participant(actor_id):
            raise NotAParticipantError()

        if request_status is not None:
            dialog = await self._dialogs.update_status(dialog_id, request_status)
            self._audit.log_event(
                "dialog.status_changed",
                dialog_id=str(dialog_id),
                external_request_id=dialog.request_id,
                request_status=request_status.value,
            )
            await self._publisher.publish_to_users(
                [p.user_id for p in dialog.participants],
                {
                    "type": "dialog.status_changed",
                    "payload": {
                        "dialog_id": str(dialog_id),
                        "request_id": dialog.request_id,
                        "request_status": request_status.value,
                    },
                },
            )

        if participants is not None:
            dialog = await self._dialogs.replace_participants(dialog_id, participants)

        if archived is not None:
            await self._states.set_archived(dialog_id, actor_id, archived)

        return dialog