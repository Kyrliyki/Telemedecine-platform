from typing import Protocol
from uuid import UUID

from chat_service.domain.entities import Message


class MessageRepository(Protocol):
    async def save(
        self,
        *,
        message_id: UUID,
        dialog_id: UUID,
        author_id: UUID,
        content: str,
        client_msg_id: UUID,
    ) -> tuple[Message, bool]:  
        ...
    async def page(
        self,
        dialog_id: UUID,
        *,
        cursor: UUID | None,
        limit: int,
        before=None,
        after=None,
        since: UUID | None = None,
    ) -> tuple[list[Message], UUID | None]: ...
    async def get(self, message_id: UUID) -> Message | None: ...
    async def edit(self, message_id: UUID, content: str) -> Message: ...
    async def soft_delete(self, message_id: UUID) -> None: ...