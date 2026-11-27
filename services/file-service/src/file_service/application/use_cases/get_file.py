"""Fetch a single file with a fresh presigned URL."""

from __future__ import annotations

from file_service.application.dto import FileInfo, FileView, GetFileQuery
from file_service.application.use_cases.common import AppDeps, build_view, require_chat_access
from file_service.domain.exceptions import NotFoundError


class GetFileUseCase:
    def __init__(self, deps: AppDeps) -> None:
        self._deps = deps

    async def __call__(self, query: GetFileQuery) -> FileView:
        deps = self._deps
        cached = await deps.cache.get(query.file_id)
        if cached is not None:
            await require_chat_access(deps, query.actor, cached.chat_id)
            return await build_view(deps, cached, as_download=query.as_download)

        async with deps.uow_factory() as uow:
            attachment = await uow.attachments.get(query.file_id)
        if attachment is None or attachment.is_deleted:
            raise NotFoundError("File not found")
        await require_chat_access(deps, query.actor, attachment.chat_id)

        info = FileInfo.from_attachment(attachment)
        await deps.cache.set(info, ttl_seconds=deps.cache_ttl_seconds)
        return await build_view(deps, info, as_download=query.as_download)
