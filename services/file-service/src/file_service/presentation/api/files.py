"""HTTP API for chat attachments."""

from __future__ import annotations

from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, File, Form, Query, Response, UploadFile, status
from fastapi.responses import RedirectResponse

from file_service.application.dto import (
    CompleteUploadCommand,
    DeleteFileCommand,
    GetFileQuery,
    InitUploadCommand,
    ListChatFilesQuery,
    UploadFileCommand,
)
from file_service.domain.exceptions import ConflictError
from file_service.infrastructure.metrics import FILES_DELETED_TOTAL, FILES_UPLOADED_TOTAL
from file_service.presentation.api.deps import CurrentActor, UseCasesDep
from file_service.presentation.api.schemas import (
    CompleteUploadRequest,
    FilePageResponse,
    FileResponse,
    InitUploadRequest,
    MeResponse,
    UploadTicketResponse,
)

router = APIRouter(tags=["files"])

CHUNK_SIZE = 1024 * 1024


@router.get("/me", response_model=MeResponse, summary="Current authenticated actor")
async def me(actor: CurrentActor) -> MeResponse:
    return MeResponse(id=actor.id, roles=sorted(actor.roles))


@router.post(
    "/chats/{chat_id}/files",
    response_model=FileResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Upload a file with multipart/form-data",
    description=(
        "Single request upload used by the web client (drag & drop / file picker). "
        "The body is streamed to MinIO without buffering the whole file in memory."
    ),
)
async def upload_file(
    chat_id: UUID,
    actor: CurrentActor,
    use_cases: UseCasesDep,
    file: Annotated[UploadFile, File(description="File to attach to the chat")],
    message_id: Annotated[UUID | None, Form()] = None,
) -> FileResponse:
    async def stream():
        try:
            while chunk := await file.read(CHUNK_SIZE):
                yield chunk
        finally:
            await file.close()

    view = await use_cases.upload_file(
        UploadFileCommand(
            actor=actor,
            chat_id=chat_id,
            file_name=file.filename,
            content_type=file.content_type,
            stream=stream(),
            declared_size=getattr(file, "size", None),
            message_id=message_id,
        )
    )
    FILES_UPLOADED_TOTAL.labels(view.info.kind.value).inc()
    return FileResponse.from_view(view)


@router.post(
    "/chats/{chat_id}/uploads",
    response_model=UploadTicketResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Start a presigned (large file) upload",
    description=(
        "Creates a PENDING attachment and returns a presigned PUT URL. "
        "After the client uploads bytes directly to MinIO it must call POST /files/{id}/complete."
    ),
)
async def init_upload(
    chat_id: UUID,
    payload: InitUploadRequest,
    actor: CurrentActor,
    use_cases: UseCasesDep,
) -> UploadTicketResponse:
    ticket = await use_cases.init_upload(
        InitUploadCommand(
            actor=actor,
            chat_id=chat_id,
            file_name=payload.file_name,
            content_type=payload.content_type,
            size_bytes=payload.size_bytes,
            message_id=payload.message_id,
        )
    )
    return UploadTicketResponse(
        file=FileResponse.from_info(ticket.info),
        upload_url=ticket.upload_url,
        method=ticket.method,
        headers=ticket.headers,
        expires_at=ticket.expires_at,
    )


@router.post(
    "/files/{file_id}/complete",
    response_model=FileResponse,
    summary="Complete a presigned upload",
)
async def complete_upload(
    file_id: UUID,
    actor: CurrentActor,
    use_cases: UseCasesDep,
    payload: CompleteUploadRequest | None = None,
) -> FileResponse:
    view = await use_cases.complete_upload(
        CompleteUploadCommand(
            actor=actor,
            file_id=file_id,
            checksum_sha256=payload.checksum_sha256 if payload else None,
        )
    )
    FILES_UPLOADED_TOTAL.labels(view.info.kind.value).inc()
    return FileResponse.from_view(view)


@router.get(
    "/chats/{chat_id}/files",
    response_model=FilePageResponse,
    summary="File history of a chat (newest first)",
)
async def list_chat_files(
    chat_id: UUID,
    actor: CurrentActor,
    use_cases: UseCasesDep,
    limit: Annotated[int, Query(ge=1, le=100)] = 30,
    cursor: Annotated[str | None, Query(max_length=512)] = None,
) -> FilePageResponse:
    page = await use_cases.list_chat_files(
        ListChatFilesQuery(actor=actor, chat_id=chat_id, limit=limit, cursor=cursor)
    )
    return FilePageResponse(
        items=[FileResponse.from_view(item) for item in page.items],
        next_cursor=page.next_cursor,
    )


@router.get(
    "/files/{file_id}",
    response_model=FileResponse,
    summary="File metadata with a fresh presigned download URL",
)
async def get_file(
    file_id: UUID,
    actor: CurrentActor,
    use_cases: UseCasesDep,
    download: Annotated[bool, Query(description="Request an attachment disposition")] = False,
) -> FileResponse:
    view = await use_cases.get_file(GetFileQuery(actor=actor, file_id=file_id, as_download=download))
    return FileResponse.from_view(view)


@router.get(
    "/files/{file_id}/download",
    summary="Redirect to a presigned download URL",
    response_class=RedirectResponse,
    status_code=status.HTTP_307_TEMPORARY_REDIRECT,
)
async def download_file(file_id: UUID, actor: CurrentActor, use_cases: UseCasesDep) -> RedirectResponse:
    view = await use_cases.get_file(GetFileQuery(actor=actor, file_id=file_id, as_download=True))
    if not view.download_url:
        raise ConflictError("File is not ready for download")
    return RedirectResponse(view.download_url, status_code=status.HTTP_307_TEMPORARY_REDIRECT)


@router.delete(
    "/files/{file_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Delete a file (uploader or moderator)",
)
async def delete_file(file_id: UUID, actor: CurrentActor, use_cases: UseCasesDep) -> Response:
    await use_cases.delete_file(DeleteFileCommand(actor=actor, file_id=file_id))
    FILES_DELETED_TOTAL.inc()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
