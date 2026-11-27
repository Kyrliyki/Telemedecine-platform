from uuid import UUID

from chat_service.application.ports.dialog_repository import DialogRepository
from chat_service.application.ports.message_repository import MessageRepository
from chat_service.domain.entities import Message
from chat_service.domain.exceptions import DialogNotFoundError, NotAParticipantError


class GetHistory:
    def __init__(self, dialogs: DialogRepository, messages: MessageRepository) -> None:
        self._dialogs = dialogs
        self._messages = messages

    async def execute(
        self,
        *,
        dialog_id: UUID,
        user_id: UUID,
        cursor: UUID | None,
        limit: int,
        since: UUID | None = None,
    ) -> tuple[list[Message], UUID | None]:
        dialog = await self._dialogs.get(dialog_id)
        if dialog is None:
            raise DialogNotFoundError()
        if not dialog.is_participant(user_id):
            raise NotAParticipantError()
        return await self._messages.page(dialog_id, cursor=cursor, limit=limit, since=since)