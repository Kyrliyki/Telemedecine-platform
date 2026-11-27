"""Pydantic request/response schemas (HTTP contract)."""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field

from file_service.application.dto import FileInfo, FileView
from file_service.domain.enums import FileKind, FileStatus


class FileResponse(BaseModel):
    id: UUID
    chat_id: UUID
    uploader_id: UUID
    file_name: str
    content_type: str
    kind: FileKind
    size_bytes: int
    status: FileStatus
    checksum_sha256: str | None = None
    message_id: UUID | None = None
    created_at: datetime
    download_url: str | None = None
    download_url_expires_at: datetime | None = None

    @classmethod
    def from_view(cls, view: FileView) -> FileResponse:
        return cls._from_info(
            view.info,
            download_url=view.download_url,
            download_url_expires_at=view.download_url_expires_at,
        )

    @classmethod
    def from_info(cls, info: FileInfo) -> FileResponse:
        return cls._from_info(info)

    @classmethod
    def _from_info(
        cls,
        info: FileInfo,
        *,
        download_url: str | None = None,
        download_url_expires_at: datetime | None = None,
    ) -> FileResponse:
        return cls(
            id=info.id,
            chat_id=info.chat_id,
            uploader_id=info.uploader_id,
            file_name=info.file_name,
            content_type=info.content_type,
            kind=info.kind,
            size_bytes=info.size_bytes,
            status=info.status,
            checksum_sha256=info.checksum_sha256,
            message_id=info.message_id,
            created_at=info.created_at,
            download_url=download_url,
            download_url_expires_at=download_url_expires_at,
        )


class FilePageResponse(BaseModel):
    items: list[FileResponse]
    next_cursor: str | None = Field(
        default=None,
        description="Opaque cursor for the next (older) page. Pass it as ?cursor=",
    )


class InitUploadRequest(BaseModel):
    file_name: str = Field(min_length=1, max_length=255, examples=["МРТ_колено.pdf"])
    content_type: str | None = Field(default=None, examples=["application/pdf"])
    size_bytes: int = Field(gt=0, le=10 * 1024 * 1024 * 1024, examples=[1048576])
    message_id: UUID | None = None


class UploadTicketResponse(BaseModel):
    file: FileResponse
    upload_url: str
    method: str = "PUT"
    headers: dict[str, str] = Field(default_factory=dict)
    expires_at: datetime


class CompleteUploadRequest(BaseModel):
    checksum_sha256: str | None = Field(default=None, pattern=r"^[a-fA-F0-9]{64}$")


class MeResponse(BaseModel):
    id: UUID
    roles: list[str]


class ErrorDetail(BaseModel):
    code: str
    message: str
    details: object | None = None


class ErrorResponse(BaseModel):
    error: ErrorDetail
