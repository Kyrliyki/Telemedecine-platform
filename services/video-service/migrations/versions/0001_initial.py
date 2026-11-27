"""Initial video schema: calls and active-appointment uniqueness.

Revision ID: 0001_initial
Revises:
Create Date: 2026-11-26
"""

from __future__ import annotations

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0001_initial"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "calls",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("appointment_id", sa.Uuid(), nullable=False),
        sa.Column("room_name", sa.String(length=64), nullable=False),
        sa.Column("doctor_id", sa.String(length=128), nullable=False),
        sa.Column("patient_id", sa.String(length=128), nullable=False),
        sa.Column("status", sa.String(length=16), nullable=False),
        sa.Column("max_participants", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("started_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("ended_at", sa.DateTime(timezone=True), nullable=True),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("room_name", name="uq_calls_room_name"),
    )
    op.create_index("ix_calls_appointment_id", "calls", ["appointment_id"])
    op.create_index("ix_calls_doctor_id", "calls", ["doctor_id"])
    op.create_index("ix_calls_patient_id", "calls", ["patient_id"])
    op.create_index("ix_calls_status", "calls", ["status"])
    op.create_index(
        "uq_calls_active_appointment",
        "calls",
        ["appointment_id"],
        unique=True,
        postgresql_where=sa.text("status <> 'ended'"),
    )


def downgrade() -> None:
    """First-start migration: create-only by design; no destructive rollback."""
    pass
