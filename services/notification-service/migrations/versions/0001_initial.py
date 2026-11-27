"""Initial NotificationService schema.

Revision ID: 0001_initial
Revises:
Create Date: 2026-11-24
"""

from __future__ import annotations

from collections.abc import Sequence

from alembic import op

revision: str = "0001_initial"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.execute(
        """
        CREATE TABLE IF NOT EXISTS notification (
            id UUID PRIMARY KEY,
            user_id VARCHAR(128) NOT NULL,
            kind VARCHAR(64) NOT NULL,
            channel VARCHAR(16) NOT NULL,
            title VARCHAR(255) NOT NULL,
            body TEXT NOT NULL,
            payload JSONB NOT NULL DEFAULT '{}'::jsonb,
            status VARCHAR(16) NOT NULL,
            attempts INTEGER NOT NULL DEFAULT 0,
            dedup_key VARCHAR(512) NOT NULL,
            recipient_email VARCHAR(320),
            correlation_id VARCHAR(255),
            error TEXT,
            next_attempt_at TIMESTAMPTZ,
            processing_started_at TIMESTAMPTZ,
            created_at TIMESTAMPTZ NOT NULL,
            sent_at TIMESTAMPTZ,
            read_at TIMESTAMPTZ
        )
        """
    )
    op.execute("CREATE INDEX IF NOT EXISTS ix_notification_user_id ON notification (user_id)")
    op.execute(
        "CREATE INDEX IF NOT EXISTS ix_notification_user_created "
        "ON notification (user_id, created_at)"
    )
    op.execute(
        "CREATE INDEX IF NOT EXISTS ix_notification_pending "
        "ON notification (status, next_attempt_at, created_at)"
    )
    op.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_dedup ON notification (dedup_key)"
    )

    op.execute(
        """
        CREATE TABLE IF NOT EXISTS notification_contact (
            user_id VARCHAR(128) PRIMARY KEY,
            email VARCHAR(320) NOT NULL,
            updated_at TIMESTAMPTZ NOT NULL
        )
        """
    )

    op.execute(
        """
        CREATE TABLE IF NOT EXISTS outbox_event (
            id UUID PRIMARY KEY,
            event_id UUID NOT NULL UNIQUE,
            event_type VARCHAR(255) NOT NULL,
            service VARCHAR(255) NOT NULL,
            payload TEXT NOT NULL,
            correlation_id VARCHAR(255),
            status VARCHAR(32) NOT NULL,
            created_at TIMESTAMPTZ NOT NULL,
            sent_at TIMESTAMPTZ,
            retry_count INTEGER NOT NULL DEFAULT 0,
            last_error TEXT,
            next_attempt_at TIMESTAMPTZ,
            processing_started_at TIMESTAMPTZ
        )
        """
    )
    op.execute(
        "CREATE INDEX IF NOT EXISTS idx_outbox_pending "
        "ON outbox_event (service, status, next_attempt_at, created_at)"
    )
    op.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_outbox_event_id ON outbox_event (event_id)"
    )

    op.execute(
        """
        CREATE TABLE IF NOT EXISTS processed_event (
            event_id UUID PRIMARY KEY,
            event_type VARCHAR(255) NOT NULL,
            processed_at TIMESTAMPTZ NOT NULL
        )
        """
    )
    op.execute(
        "CREATE INDEX IF NOT EXISTS idx_processed_event_processed_at "
        "ON processed_event (processed_at)"
    )


def downgrade() -> None:
    """First-start migration: create-only by design; no destructive rollback."""
    pass
