from typing import Protocol
from uuid import UUID

from chat_service.domain.entities import Dialog, Participant
from chat_service.domain.value_objects import RequestStatus


class DialogRepository(Protocol):
    async def get(self, dialog_id: UUID) -> Dialog | None: ...
    async def get_by_request(self, request_id: str) -> Dialog | None: ...
    async def create_idempotent(
        self, request_id: str, participants: list[Participant], status: RequestStatus
    ) -> tuple[Dialog, bool]:  
        ...
    async def list_for_user(
        self,
        user_id: UUID,
        *,
        cursor: str | None,
        limit: int,
        archived: bool | None,
        request_status: RequestStatus | None,
    ) -> tuple[list[Dialog], str | None]: ...
    async def update_status(self, dialog_id: UUID, status: RequestStatus) -> Dialog: ...
    async def replace_participants(
        self, dialog_id: UUID, participants: list[Participant]
    ) -> Dialog: ...
    async def touch(self, dialog_id: UUID) -> None: ...
    async def delete(self, dialog_id: UUID) -> bool: ...
    async def last_message(self, dialog_id: UUID): ...