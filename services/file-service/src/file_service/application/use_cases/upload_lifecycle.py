"""Two-step upload for large files: presigned PUT + complete."""

from __future__ import annotations

import logging
from datetime import timedelta

from file_service.application.dto import (
    CompleteUploadCommand,
    FileInfo,
    FileView,
    InitUploadCommand,
    UploadTicket,
)
from file_service.application.use_cases.common import AppDeps, build_view_from_attachment, require_chat_access
from file_service.domain.entities import Attachment
from file_service.domain.enums import FileStatus
from file_service.domain.exceptions import ConflictError, FileTooLargeError, NotFoundError, ValidationError
from file_service.domain.value_objects import build_storage_key

logger = logging.getLogger(__name__)

class InitUploadUseCase:
    def __init__(self, deps: AppDeps) -> None:
        self._deps = deps

    async def __call__(self, command: InitUploadCommand) -> UploadTicket:
        deps = self._deps
        await require_chat_access(deps, command.actor, command.chat_id)
        prepared = deps.policy.prepare(
            file_name=command.file_name,
            content_type=command.content_type,
            size_bytes=command.size_bytes,
        )

        file_id = deps.ids.new_id()
        storage_key = build_storage_key(command.chat_id, file_id, prepared.file_name)
        now = deps.clock.now()
        attachment = Attachment.create_pending(
            file_id=file_id,
            chat_id=command.chat_id,
            uploader_id=command.actor.id,
            file_name=prepared.file_name,
            content_type=prepared.content_type,
            kind=prepared.kind,
            declared_size=command.size_bytes,
            storage_key=storage_key,
            bucket=deps.bucket,
            now=now,
            message_id=command.message_id,
        )

        async with deps.uow_factory() as uow:
            await uow.attachments.add(attachment)
            await uow.commit()

        upload_url = await deps.storage.presigned_put(
            storage_key,
            content_type=prepared.content_type,
            ttl_seconds=deps.presign_ttl_seconds,
        )
        return UploadTicket(
            info=FileInfo.from_attachment(attachment),
            upload_url=upload_url,
            method="PUT",
            headers={"Content-Type": prepared.content_type},
            expires_at=now + timedelta(seconds=deps.presign_ttl_seconds),
        )

class CompleteUploadUseCase:
    def __init__(self, deps: AppDeps) -> None:
        self._deps = deps

    async def __call__(self, command: CompleteUploadCommand) -> FileView:
        deps = self._deps
        async with deps.uow_factory() as uow:
            attachment = await uow.attachments.get(command.file_id)
        if attachment is None or attachment.is_deleted:
            raise NotFoundError("File not found")
        await require_chat_access(deps, command.actor, attachment.chat_id)

        if attachment.status is FileStatus.READY:
            return await build_view_from_attachment(deps, attachment)
        if attachment.status is not FileStatus.PENDING:
            raise ConflictError(f"Cannot complete upload in status '{attachment.status}'")

        stored = await deps.storage.stat(attachment.storage_key)
        if stored is None:
            raise ConflictError("Uploaded object was not found in storage")
        if stored.size_bytes <= 0:
            raise ValidationError("Uploaded file is empty")
        if stored.size_bytes > deps.policy.max_size_bytes:
            await deps.storage.delete(attachment.storage_key)
            raise FileTooLargeError(
                f"Uploaded file is too large: {stored.size_bytes} bytes, "
                f"maximum is {deps.policy.max_size_bytes} bytes"
            )

        
        async with deps.uow_factory() as uow:
            attachment = await uow.attachments.get(command.file_id, for_update=True)
            if attachment is None or attachment.is_deleted:
                raise NotFoundError("File not found")
            if attachment.status is FileStatus.PENDING:
                attachment.mark_ready(
                    size_bytes=stored.size_bytes,
                    checksum_sha256=command.checksum_sha256 or stored.checksum_sha256,
                    now=deps.clock.now(),
                )
                await uow.attachments.update(attachment)
                await uow.outbox.add(attachment.uploaded_event())
                await uow.commit()
                await deps.cache.delete(attachment.id)
            elif attachment.status is not FileStatus.READY:
                raise ConflictError(f"Cannot complete upload in status '{attachment.status}'")

        return await build_view_from_attachment(deps, attachment)
