"""Soft delete a file and remove the object from S3."""

from __future__ import annotations

import logging

from file_service.application.dto import DeleteFileCommand
from file_service.application.use_cases.common import AppDeps, require_chat_access
from file_service.domain.exceptions import NotFoundError

logger = logging.getLogger(__name__)

class DeleteFileUseCase:
    def __init__(self, deps: AppDeps) -> None:
        self._deps = deps

    async def __call__(self, command: DeleteFileCommand) -> bool:
        deps = self._deps
        async with deps.uow_factory() as uow:
            attachment = await uow.attachments.get(command.file_id, for_update=True)
            if attachment is None or attachment.is_deleted:
                raise NotFoundError("File not found")
            await require_chat_access(deps, command.actor, attachment.chat_id)

            changed = attachment.soft_delete(
                actor_id=command.actor.id,
                roles=command.actor.roles,
                now=deps.clock.now(),
            )
            if not changed:
                return False
            await uow.attachments.update(attachment)
            await uow.outbox.add(attachment.deleted_event(deleted_by=command.actor.id))
            await uow.commit()

        await deps.cache.delete(attachment.id)
        try:
            await deps.storage.delete(attachment.storage_key)
        except Exception:
            
            logger.warning("failed to delete storage object %s", attachment.storage_key, exc_info=True)
        return True
