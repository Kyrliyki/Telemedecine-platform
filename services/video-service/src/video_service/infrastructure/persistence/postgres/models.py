"""ORM-модели. Не используются в domain/application слоях."""

from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import DateTime, Index, Integer, String, Uuid, text
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    pass

class CallModel(Base):
    __tablename__ = "calls"
    
    
    __table_args__ = (
        Index(
            "uq_calls_active_appointment",
            "appointment_id",
            unique=True,
            postgresql_where=text("status <> 'ended'"),
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True)
    appointment_id: Mapped[uuid.UUID] = mapped_column(
        Uuid,
        index=True,
        nullable=False,
    )
    room_name: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    doctor_id: Mapped[str] = mapped_column(String(128), index=True, nullable=False)
    patient_id: Mapped[str] = mapped_column(String(128), index=True, nullable=False)
    status: Mapped[str] = mapped_column(String(16), index=True, nullable=False)
    max_participants: Mapped[int] = mapped_column(Integer, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    started_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    ended_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
