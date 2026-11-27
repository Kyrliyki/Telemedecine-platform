"""Internal FileService endpoints used by Java EMR services."""

from __future__ import annotations

from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, Request, status
from sqlalchemy import select

from file_service.infrastructure.db.models import FileAttachmentModel
from file_service.presentation.api.internal_auth import internal_token_guard

router = APIRouter(
    prefix="/internal",
    tags=["internal"],
    dependencies=[Depends(internal_token_guard())],
)


@router.get(
    "/files/{file_id}/metadata",
    summary="Internal file metadata (Java services)",
)
async def file_metadata(
    file_id: UUID,
    request: Request,
    owner_id: Annotated[UUID | None, Query(alias="ownerId")] = None,
) -> dict[str, str]:
    container = request.app.state.container
    async with container.session_factory() as session:
        result = await session.execute(
            select(FileAttachmentModel).where(FileAttachmentModel.id == file_id)
        )
        attachment = result.scalar_one_or_none()

    if attachment is None or attachment.deleted_at is not None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="File not found",
        )
    if owner_id is not None and attachment.uploader_id != owner_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="File does not belong to the requested owner",
        )

    return {
        "fileId": str(attachment.id),
        "ownerId": str(attachment.uploader_id),
        "status": attachment.status.upper(),
    }
