"""Broadcast a typing indicator to dialog participants."""

from __future__ import annotations

from uuid import UUID

from chat_service.application.ports.dialog_repository import DialogRepository
from chat_service.application.ports.publisher import EventPublisher
from chat_service.domain.exceptions import DialogNotFoundError, NotAParticipantError


class SendTyping:
    def __init__(self, dialogs: DialogRepository, publisher: EventPublisher) -> None:
        self._dialogs = dialogs
        self._publisher = publisher

    async def execute(self, *, dialog_id: UUID, user_id: UUID) -> None:
        dialog = await self._dialogs.get(dialog_id)
        if dialog is None:
            raise DialogNotFoundError()
        if not dialog.is_participant(user_id):
            raise NotAParticipantError()

        recipients = [
            participant.user_id
            for participant in dialog.participants
            if participant.user_id != user_id
        ]
        await self._publisher.publish_to_users(
            recipients,
            {
                "type": "typing",
                "payload": {
                    "dialog_id": str(dialog_id),
                    "user_id": str(user_id),
                },
            },
        )
