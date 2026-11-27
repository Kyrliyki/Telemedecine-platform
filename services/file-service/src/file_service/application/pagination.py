"""Opaque cursor helpers for keyset pagination."""

from __future__ import annotations

import base64
import json
from datetime import datetime
from uuid import UUID

from file_service.domain.exceptions import ValidationError


def encode_cursor(created_at: datetime, item_id: UUID) -> str:
    raw = json.dumps({"created_at": created_at.isoformat(), "id": str(item_id)}, separators=(",", ":"))
    return base64.urlsafe_b64encode(raw.encode("utf-8")).decode("ascii").rstrip("=")


def decode_cursor(cursor: str) -> tuple[datetime, UUID]:
    try:
        padded = cursor + "=" * (-len(cursor) % 4)
        raw = base64.urlsafe_b64decode(padded.encode("ascii")).decode("utf-8")
        data = json.loads(raw)
        created_at = datetime.fromisoformat(data["created_at"])
        item_id = UUID(data["id"])
    except (ValueError, KeyError, TypeError, json.JSONDecodeError) as exc:
        raise ValidationError("Invalid pagination cursor") from exc
    if created_at.tzinfo is None:
        raise ValidationError("Invalid pagination cursor")
    return created_at, item_id
