"""File related value objects: names, content types and the upload policy."""

from __future__ import annotations

import mimetypes
import re
from dataclasses import dataclass
from pathlib import PurePosixPath
from uuid import UUID

from file_service.domain.enums import FileKind
from file_service.domain.exceptions import FileTooLargeError, ValidationError

_UNSAFE_CHARS = re.compile(r"[\x00-\x1f\x7f]")
_MAX_NAME_LENGTH = 255
_MAX_EXTENSION_LENGTH = 16

_IMAGE_TYPES = frozenset(
    {"image/jpeg", "image/png", "image/gif", "image/webp", "image/heic", "image/svg+xml"}
)
_VIDEO_TYPES = frozenset({"video/mp4", "video/webm", "video/quicktime", "video/x-matroska"})
_AUDIO_TYPES = frozenset({"audio/mpeg", "audio/ogg", "audio/wav", "audio/x-wav", "audio/webm", "audio/mp4"})
_ARCHIVE_TYPES = frozenset(
    {
        "application/zip",
        "application/x-7z-compressed",
        "application/x-rar-compressed",
        "application/gzip",
        "application/x-tar",
    }
)


def normalize_file_name(raw_name: str | None) -> str:
    """Return a safe display name without any path components."""
    if not raw_name:
        raise ValidationError("File name is required")

    name = raw_name.replace("\\", "/").split("/")[-1].strip()
    name = _UNSAFE_CHARS.sub("", name)
    if not name or name in {".", ".."}:
        raise ValidationError("File name is invalid")
    if len(name) > _MAX_NAME_LENGTH:
        suffix = PurePosixPath(name).suffix[: _MAX_EXTENSION_LENGTH + 1]
        name = name[: _MAX_NAME_LENGTH - len(suffix)] + suffix
    return name


def normalize_content_type(raw_content_type: str | None, file_name: str) -> str:
    content_type = (raw_content_type or "").split(";")[0].strip().lower()
    if not content_type or content_type == "application/octet-stream":
        guessed, _ = mimetypes.guess_type(file_name)
        return guessed or "application/octet-stream"
    return content_type


def classify_content_type(content_type: str) -> FileKind:
    if content_type in _IMAGE_TYPES or content_type.startswith("image/"):
        return FileKind.IMAGE
    if content_type in _VIDEO_TYPES or content_type.startswith("video/"):
        return FileKind.VIDEO
    if content_type in _AUDIO_TYPES or content_type.startswith("audio/"):
        return FileKind.AUDIO
    if content_type in _ARCHIVE_TYPES:
        return FileKind.ARCHIVE
    if content_type.startswith("text/") or (
        content_type.startswith("application/")
        and content_type
        not in {"application/octet-stream", "application/x-msdownload", "application/x-msdos-program"}
    ):
        return FileKind.DOCUMENT
    return FileKind.OTHER


def build_storage_key(chat_id: UUID, file_id: UUID, file_name: str) -> str:
    """Build an opaque, URL safe object key. The original name lives only in the DB."""
    suffix = PurePosixPath(file_name).suffix.lower()
    if len(suffix) > _MAX_EXTENSION_LENGTH:
        suffix = ""
    if not re.fullmatch(r"\.[a-z0-9]{1,16}", suffix):
        suffix = ""
    return f"chats/{chat_id}/{file_id.hex}{suffix}"


def content_type_is_allowed(content_type: str, allowed: frozenset[str] | None) -> bool:
    if not allowed or "*/*" in allowed or "*" in allowed:
        return True
    for pattern in allowed:
        if pattern == content_type:
            return True
        if pattern.endswith("/*") and content_type.startswith(pattern[:-1]):
            return True
    return False


@dataclass(frozen=True, slots=True)
class PreparedFile:
    file_name: str
    content_type: str
    kind: FileKind


@dataclass(frozen=True, slots=True)
class UploadPolicy:
    """Pure domain policy shared by all upload scenarios."""

    max_size_bytes: int = 100 * 1024 * 1024
    allowed_content_types: frozenset[str] | None = None

    def prepare(
        self,
        *,
        file_name: str | None,
        content_type: str | None,
        size_bytes: int | None,
    ) -> PreparedFile:
        name = normalize_file_name(file_name)
        normalized_type = normalize_content_type(content_type, name)
        if not content_type_is_allowed(normalized_type, self.allowed_content_types):
            raise ValidationError(f"Content type '{normalized_type}' is not allowed")
        if size_bytes is not None:
            if size_bytes <= 0:
                raise ValidationError("File is empty")
            if size_bytes > self.max_size_bytes:
                raise FileTooLargeError(
                    f"File is too large: {size_bytes} bytes, maximum is {self.max_size_bytes} bytes"
                )
        return PreparedFile(
            file_name=name,
            content_type=normalized_type,
            kind=classify_content_type(normalized_type),
        )
