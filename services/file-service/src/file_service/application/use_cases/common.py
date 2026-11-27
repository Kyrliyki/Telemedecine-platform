"""Shared dependencies and helpers for use cases."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import timedelta
from uuid import UUID

from file_service.application.dto import Actor, FileInfo, FileView
from file_service.application.ports import (
    ChatAccessPolicy,
    Clock,
    FileCache,
    IdGenerator,
    ObjectStorage,
    UnitOfWorkFactory,
)
from file_service.domain.entities import Attachment
from file_service.domain.enums import FileKind
from file_service.domain.exceptions import ForbiddenError
from file_service.domain.value_objects import UploadPolicy


@dataclass(frozen=True, slots=True)
class AppDeps:
    uow_factory: UnitOfWorkFactory
    storage: ObjectStorage
    cache: FileCache
    clock: Clock
    ids: IdGenerator
    policy: UploadPolicy
    chat_access: ChatAccessPolicy
    bucket: str
    presign_ttl_seconds: int = 900
    cache_ttl_seconds: int = 60

async def require_chat_access(deps: AppDeps, actor: Actor, chat_id: UUID) -> None:
    if not await deps.chat_access.can_access(actor, chat_id):
        raise ForbiddenError("You do not have access to this chat")

async def build_view(deps: AppDeps, info: FileInfo, *, as_download: bool = False) -> FileView:
    if info.status.value != "ready":
        return FileView(info=info)
    
    
    previewable = info.kind in {FileKind.IMAGE, FileKind.VIDEO, FileKind.AUDIO}
    download_name = info.file_name if as_download or not previewable else None
    url = await deps.storage.presigned_get(
        info.storage_key,
        ttl_seconds=deps.presign_ttl_seconds,
        download_name=download_name,
    )
    expires_at = deps.clock.now() + timedelta(seconds=deps.presign_ttl_seconds)
    return FileView(info=info, download_url=url, download_url_expires_at=expires_at)

async def build_view_from_attachment(
    deps: AppDeps,
    attachment: Attachment,
    *,
    as_download: bool = False,
) -> FileView:
    return await build_view(deps, FileInfo.from_attachment(attachment), as_download=as_download)
