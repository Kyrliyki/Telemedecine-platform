"""Inbound integration event handlers."""

from __future__ import annotations

import logging
from typing import Any
from uuid import UUID

from file_service.application.use_cases.purge_chat_files import PurgeChatFilesUseCase
from file_service.domain.exceptions import ValidationError

logger = logging.getLogger(__name__)


class ChatEventsHandler:
    def __init__(self, purge_chat_files: PurgeChatFilesUseCase) -> None:
        self._purge_chat_files = purge_chat_files

    async def __call__(self, envelope: dict[str, Any]) -> None:
        event_type = envelope.get("event_type") or envelope.get("type")
        if event_type != "chat.deleted":
            logger.debug("ignoring chat event", extra={"event_type": event_type})
            return

        payload = envelope.get("payload") or {}
        raw_chat_id = payload.get("chat_id")
        if not raw_chat_id:
            raise ValidationError("chat.deleted event has no chat_id")
        chat_id = UUID(str(raw_chat_id))
        purged = await self._purge_chat_files(chat_id)
        logger.info("processed chat.deleted", extra={"chat_id": str(chat_id), "files": purged})
