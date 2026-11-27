"""Delete a dialog and notify downstream services."""

from __future__ import annotations

from uuid import UUID

from chat_service.application.ports.audit_logger import AuditLogger
from chat_service.application.ports.dialog_repository import DialogRepository
from chat_service.application.ports.publisher import ChatEventsPublisher
from chat_service.domain.exceptions import DialogNotFoundError, NotAParticipantError


class DeleteDialog:
    def __init__(
        self,
        dialogs: DialogRepository,
        events: ChatEventsPublisher | None,
        audit: AuditLogger,
    ) -> None:
        self._dialogs = dialogs
        self._events = events
        self._audit = audit

    async def execute(self, *, dialog_id: UUID, actor_id: UUID) -> None:
        dialog = await self._dialogs.get(dialog_id)
        if dialog is None:
            raise DialogNotFoundError()
        if not dialog.is_participant(actor_id):
            raise NotAParticipantError()

        deleted = await self._dialogs.delete(dialog_id)
        if not deleted:
            raise DialogNotFoundError()

        self._audit.log_event(
            "dialog.delete",
            dialog_id=str(dialog_id),
            external_request_id=dialog.request_id,
            actor_id=str(actor_id),
            participant_ids=[str(participant.user_id) for participant in dialog.participants],
        )
        if self._events is not None:
            await self._events.publish_deleted(
                chat_id=dialog_id,
                request_id=dialog.request_id,
            )
