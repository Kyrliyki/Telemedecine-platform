"""Direct (multipart / streamed) file upload."""

from __future__ import annotations

import logging

from file_service.application.dto import FileView, UploadFileCommand
from file_service.application.use_cases.common import AppDeps, build_view_from_attachment, require_chat_access
from file_service.domain.entities import Attachment
from file_service.domain.exceptions import ValidationError
from file_service.domain.value_objects import build_storage_key

logger = logging.getLogger(__name__)


class UploadFileUseCase:
    """Two-phase upload: persist a PENDING row, stream bytes to S3, mark READY.

    The row is committed before the S3 call so a crash leaves a PENDING record that can be
    inspected/cleaned up instead of a dangling object. On a failed upload we compensate by
    marking the row FAILED and removing a possibly partially written object.
    """

    def __init__(self, deps: AppDeps) -> None:
        self._deps = deps

    async def __call__(self, command: UploadFileCommand) -> FileView:
        deps = self._deps
        await require_chat_access(deps, command.actor, command.chat_id)

        prepared = deps.policy.prepare(
            file_name=command.file_name,
            content_type=command.content_type,
            size_bytes=command.declared_size,
        )
        declared_size = command.declared_size or 0
        if declared_size < 0:
            raise ValidationError("File size cannot be negative")

        file_id = deps.ids.new_id()
        storage_key = build_storage_key(command.chat_id, file_id, prepared.file_name)
        attachment = Attachment.create_pending(
            file_id=file_id,
            chat_id=command.chat_id,
            uploader_id=command.actor.id,
            file_name=prepared.file_name,
            content_type=prepared.content_type,
            kind=prepared.kind,
            declared_size=declared_size,
            storage_key=storage_key,
            bucket=deps.bucket,
            now=deps.clock.now(),
            message_id=command.message_id,
        )

        async with deps.uow_factory() as uow:
            await uow.attachments.add(attachment)
            await uow.commit()

        try:
            stored = await deps.storage.put_stream(
                storage_key,
                command.stream,
                content_type=prepared.content_type,
                max_bytes=deps.policy.max_size_bytes,
            )
        except Exception as exc:
            logger.warning(
                "file upload failed, marking attachment as failed",
                extra={"file_id": str(file_id)},
            )
            attachment.mark_failed(now=deps.clock.now(), reason=type(exc).__name__)
            async with deps.uow_factory() as uow:
                await uow.attachments.update(attachment)
                await uow.commit()
            try:
                await deps.storage.delete(storage_key)
            except Exception:
                logger.warning("failed to clean up object after failed upload", exc_info=True)
            raise

        attachment.mark_ready(
            size_bytes=stored.size_bytes,
            checksum_sha256=stored.checksum_sha256,
            now=deps.clock.now(),
        )
        async with deps.uow_factory() as uow:
            await uow.attachments.update(attachment)
            await uow.outbox.add(attachment.uploaded_event())
            await uow.commit()

        await deps.cache.delete(file_id)
        return await build_view_from_attachment(deps, attachment)
