import hashlib
import logging
from typing import Any
from uuid import UUID

_audit = logging.getLogger("chat.audit")
_tech = logging.getLogger("chat")


class JsonAuditLogger:
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
    ) -> None:
        _audit.info(
            "message.audit",
            extra={
                "event": "message.audit",
                "message_id": str(message_id),
                "dialog_id": str(dialog_id),
                "external_request_id": external_request_id,
                "author_id": str(author_id),
                "recipient_ids": [str(r) for r in recipient_ids],
                "content_hash": hashlib.sha256(content.encode()).hexdigest(),
                "content_length": len(content),
                "created_at": created_at,
            },
        )

    def log_event(self, event: str, **fields: Any) -> None:
        _tech.info(event, extra={"event": event, **fields})