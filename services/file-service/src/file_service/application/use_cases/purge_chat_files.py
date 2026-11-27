"""Async reaction to ChatService events: remove all files of a deleted chat."""

from __future__ import annotations

import asyncio
import logging
from uuid import UUID

from file_service.application.use_cases.common import AppDeps

logger = logging.getLogger(__name__)

_DELETE_CONCURRENCY = 8


class PurgeChatFilesUseCase:
    def __init__(self, deps: AppDeps) -> None:
        self._deps = deps

    async def __call__(self, chat_id: UUID) -> int:
        deps = self._deps
        async with deps.uow_factory() as uow:
            attachments = await uow.attachments.list_all_by_chat(chat_id)
            now = deps.clock.now()
            expired = []
            for attachment in attachments:
                if attachment.expire(now=now):
                    expired.append(attachment)
            if not expired:
                return 0
            for attachment in expired:
                await uow.attachments.update(attachment)
                await uow.outbox.add(attachment.deleted_event(deleted_by=None))
            await uow.commit()

        semaphore = asyncio.Semaphore(_DELETE_CONCURRENCY)

        async def remove(storage_key: str) -> None:
            async with semaphore:
                try:
                    await deps.storage.delete(storage_key)
                except Exception:
                    logger.warning("failed to purge object %s", storage_key, exc_info=True)

        await asyncio.gather(*(remove(a.storage_key) for a in expired))
        for attachment in expired:
            await deps.cache.delete(attachment.id)
        logger.info("purged %s files from chat %s", len(expired), chat_id)
        return len(expired)
