"""Chat file history with keyset pagination."""

from __future__ import annotations

import asyncio

from file_service.application.dto import FilePage, ListChatFilesQuery
from file_service.application.pagination import decode_cursor, encode_cursor
from file_service.application.use_cases.common import AppDeps, build_view_from_attachment, require_chat_access
from file_service.domain.exceptions import ValidationError

MAX_PAGE_SIZE = 100


class ListChatFilesUseCase:
    def __init__(self, deps: AppDeps) -> None:
        self._deps = deps

    async def __call__(self, query: ListChatFilesQuery) -> FilePage:
        deps = self._deps
        if query.limit < 1 or query.limit > MAX_PAGE_SIZE:
            raise ValidationError(f"Limit must be between 1 and {MAX_PAGE_SIZE}")
        await require_chat_access(deps, query.actor, query.chat_id)
        before = decode_cursor(query.cursor) if query.cursor else None

        async with deps.uow_factory() as uow:
            rows = await uow.attachments.list_by_chat(query.chat_id, limit=query.limit + 1, before=before)

        has_more = len(rows) > query.limit
        rows = rows[: query.limit]
        views = await asyncio.gather(*(build_view_from_attachment(deps, row) for row in rows))
        next_cursor = None
        if has_more and rows:
            last = rows[-1]
            next_cursor = encode_cursor(last.created_at, last.id)
        return FilePage(items=list(views), next_cursor=next_cursor)
