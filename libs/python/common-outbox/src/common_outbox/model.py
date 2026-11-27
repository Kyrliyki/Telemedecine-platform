from __future__ import annotations

from datetime import datetime
from enum import Enum
from uuid import UUID, uuid4

from common_db import Base
from sqlalchemy import DateTime, Index, Integer, String, Text, Uuid
from sqlalchemy import Enum as SqlEnum
from sqlalchemy.orm import Mapped, mapped_column


class OutboxStatus(str, Enum):  # noqa: UP042
    PENDING = "PENDING"
    PROCESSING = "PROCESSING"
    SENT = "SENT"
    FAILED = "FAILED"
    DEAD = "DEAD"


class OutboxEvent(Base):
    __tablename__ = "outbox_event"
    __table_args__ = (
        Index("idx_outbox_pending", "service", "status", "next_attempt_at", "created_at"),
        Index("idx_outbox_event_id", "event_id", unique=True),
    )

    id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid4)
    event_id: Mapped[UUID] = mapped_column(Uuid(as_uuid=True), nullable=False, unique=True)
    event_type: Mapped[str] = mapped_column(String(255), nullable=False)
    service: Mapped[str] = mapped_column(String(255), nullable=False)
    payload: Mapped[str] = mapped_column(Text, nullable=False)
    correlation_id: Mapped[str | None] = mapped_column(String(255))
    status: Mapped[OutboxStatus] = mapped_column(
        SqlEnum(OutboxStatus, native_enum=False, length=32),
        nullable=False,
        default=OutboxStatus.PENDING,
    )
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    sent_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    retry_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    last_error: Mapped[str | None] = mapped_column(Text)
    next_attempt_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    processing_started_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
