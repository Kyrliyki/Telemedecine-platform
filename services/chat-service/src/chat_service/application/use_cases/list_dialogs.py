from uuid import UUID

from chat_service.application.ports.dialog_repository import DialogRepository
from chat_service.domain.entities import Dialog
from chat_service.domain.value_objects import RequestStatus


class ListDialogs:
    def __init__(self, dialogs: DialogRepository) -> None:
        self._dialogs = dialogs

    async def execute(
        self,
        *,
        user_id: UUID,
        cursor: str | None,
        limit: int,
        archived: bool | None,
        request_status: RequestStatus | None,
    ) -> tuple[list[Dialog], str | None]:
        return await self._dialogs.list_for_user(
            user_id,
            cursor=cursor,
            limit=limit,
            archived=archived,
            request_status=request_status,
        )