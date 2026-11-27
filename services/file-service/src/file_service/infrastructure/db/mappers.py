"""Mapping between ORM rows and domain entities."""

from __future__ import annotations

from file_service.domain.entities import Attachment
from file_service.domain.enums import FileKind, FileStatus
from file_service.infrastructure.db.models import FileAttachmentModel


def to_entity(row: FileAttachmentModel) -> Attachment:
    return Attachment(
        id=row.id,
        chat_id=row.chat_id,
        uploader_id=row.uploader_id,
        message_id=row.message_id,
        file_name=row.file_name,
        content_type=row.content_type,
        kind=FileKind(row.kind),
        status=FileStatus(row.status),
        size_bytes=row.size_bytes,
        checksum_sha256=row.checksum_sha256,
        storage_key=row.storage_key,
        bucket=row.bucket,
        metadata=dict(row.metadata_json or {}),
        created_at=row.created_at,
        updated_at=row.updated_at,
        deleted_at=row.deleted_at,
    )


def to_model(attachment: Attachment) -> FileAttachmentModel:
    return FileAttachmentModel(
        id=attachment.id,
        chat_id=attachment.chat_id,
        uploader_id=attachment.uploader_id,
        message_id=attachment.message_id,
        file_name=attachment.file_name,
        content_type=attachment.content_type,
        kind=attachment.kind.value,
        status=attachment.status.value,
        size_bytes=attachment.size_bytes,
        checksum_sha256=attachment.checksum_sha256,
        storage_key=attachment.storage_key,
        bucket=attachment.bucket,
        metadata_json=dict(attachment.metadata),
        created_at=attachment.created_at,
        updated_at=attachment.updated_at,
        deleted_at=attachment.deleted_at,
    )
