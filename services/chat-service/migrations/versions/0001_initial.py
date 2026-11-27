"""initial schema

Revision ID: 0001
Revises:
Create Date: 2026-11-22

"""
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

request_status = postgresql.ENUM(
    "open", "closed", name="request_status", create_type=False
)
participant_role = postgresql.ENUM(
    "client", "operator", name="participant_role", create_type=False
)


def upgrade() -> None:
    bind = op.get_bind()
    request_status.create(bind, checkfirst=True)
    participant_role.create(bind, checkfirst=True)

    op.create_table(
        "dialogs",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, nullable=False),
        sa.Column("request_id", sa.String(length=255), nullable=False),
        sa.Column(
            "request_status",
            request_status,
            nullable=False,
            server_default=sa.text("'open'::request_status"),
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
    )
    op.create_index("ix_dialogs_request_id", "dialogs", ["request_id"], unique=True)
    op.create_index("ix_dialogs_updated_at", "dialogs", ["updated_at"])

    op.create_table(
        "dialog_participants",
        sa.Column(
            "dialog_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("dialogs.id", ondelete="CASCADE"),
            primary_key=True,
            nullable=False,
        ),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), primary_key=True, nullable=False),
        sa.Column("role", participant_role, nullable=False),
        sa.Column(
            "joined_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
    )
    op.create_index(
        "ix_dialog_participants_user_id", "dialog_participants", ["user_id"]
    )

    op.create_table(
        "dialog_user_state",
        sa.Column(
            "dialog_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("dialogs.id", ondelete="CASCADE"),
            primary_key=True,
            nullable=False,
        ),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), primary_key=True, nullable=False),
        sa.Column("last_read_message_id", postgresql.UUID(as_uuid=True), nullable=True),
        sa.Column("unread_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("archived", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
    )

    op.create_table(
        "messages",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, nullable=False),
        sa.Column(
            "dialog_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("dialogs.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("author_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("content", sa.Text(), nullable=False),
        sa.Column("client_msg_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column("edited_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
        sa.UniqueConstraint(
            "dialog_id", "author_id", "client_msg_id", name="uq_message_dedup"
        ),
    )
    op.create_index("ix_messages_dialog_id", "messages", ["dialog_id"])


def downgrade() -> None:
    """First-start migration: create-only by design; no destructive rollback."""
    pass
