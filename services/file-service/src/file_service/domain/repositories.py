"""Repository ports owned by the domain."""

from __future__ import annotations

from datetime import datetime
from typing import Protocol
from uuid import UUID

from file_service.domain.entities import Attachment


class AttachmentRepository(Protocol):
    async def add(self, attachment: Attachment) -> None: ...

    async def get(self, file_id: UUID, *, for_update: bool = False) -> Attachment | None: ...

    async def update(self, attachment: Attachment) -> None: ...

    async def list_by_chat(
        self,
        chat_id: UUID,
        *,
        limit: int,
        before: tuple[datetime, UUID] | None = None,
    ) -> list[Attachment]:
        """Return READY attachments ordered by (created_at, id) DESC."""
        ...

    async def list_all_by_chat(self, chat_id: UUID) -> list[Attachment]: ...
