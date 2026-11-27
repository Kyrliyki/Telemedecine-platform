from uuid import UUID

from chat_service.application.ports.dialog_repository import DialogRepository
from chat_service.application.ports.message_repository import MessageRepository
from chat_service.application.ports.state_repository import DialogStateRepository
from chat_service.domain.entities import Dialog, Message
from chat_service.domain.exceptions import DialogNotFoundError, NotAParticipantError


class GetDialogByRequest:
    def __init__(
        self,
        dialogs: DialogRepository,
        messages: MessageRepository,
        states: DialogStateRepository,
    ) -> None:
        self._dialogs = dialogs
        self._messages = messages
        self._states = states

    async def execute(
        self, *, request_id: str, user_id: UUID, limit: int = 50
    ) -> tuple[Dialog, list[Message], UUID | None]:
        dialog = await self._dialogs.get_by_request(request_id)
        if dialog is None:
            raise DialogNotFoundError(f"dialog for request {request_id} not found")
        if not dialog.is_participant(user_id):
            raise NotAParticipantError()
        items, next_cursor = await self._messages.page(
            dialog.id, cursor=None, limit=limit
        )
        return dialog, items, next_cursor