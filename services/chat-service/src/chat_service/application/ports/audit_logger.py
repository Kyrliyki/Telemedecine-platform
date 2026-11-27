from typing import Protocol
from uuid import UUID


class AuditLogger(Protocol):
    def log_message(
        self,
        *,
        message_id: UUID,
        dialog_id: UUID,
        external_request_id: str,
        author_id: UUID,
        recipient_ids: list[UUID],
        content: str,
        created_at: str,
    ) -> None: ...

    def log_event(self, event: str, **fields) -> None: ...