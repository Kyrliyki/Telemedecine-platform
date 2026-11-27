from uuid import UUID

from chat_service.application.ports.dialog_repository import DialogRepository
from chat_service.application.ports.publisher import EventPublisher
from chat_service.application.ports.state_repository import DialogStateRepository
from chat_service.domain.exceptions import DialogNotFoundError, NotAParticipantError


class MarkRead:
    def __init__(
        self,
        dialogs: DialogRepository,
        states: DialogStateRepository,
        publisher: EventPublisher,
    ) -> None:
        self._dialogs = dialogs
        self._states = states
        self._publisher = publisher

    async def execute(self, *, dialog_id: UUID, user_id: UUID, up_to: UUID) -> None:
        dialog = await self._dialogs.get(dialog_id)
        if dialog is None:
            raise DialogNotFoundError()
        if not dialog.is_participant(user_id):
            raise NotAParticipantError()
        await self._states.mark_read(dialog_id, user_id, up_to)
        recipients = [p.user_id for p in dialog.participants if p.user_id != user_id]
        await self._publisher.publish_to_users(
            recipients,
            {
                "type": "dialog.read",
                "payload": {
                    "dialog_id": str(dialog_id),
                    "user_id": str(user_id),
                    "up_to_message_id": str(up_to),
                },
            },
        )