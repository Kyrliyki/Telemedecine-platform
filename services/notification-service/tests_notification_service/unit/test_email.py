"""Gmail SMTP configuration and sender tests."""

from __future__ import annotations

from datetime import UTC, datetime
from uuid import uuid4

import pytest
from pydantic import ValidationError

from notification_service.config import Settings
from notification_service.domain.entities import Notification
from notification_service.domain.enums import NotificationChannel, NotificationStatus
from notification_service.infrastructure.channels.email import SmtpEmailSender
from tests_notification_service.conftest import make_settings


def notification() -> Notification:
    return Notification(
        id=uuid4(),
        user_id="patient-1",
        kind="system.test",
        channel=NotificationChannel.EMAIL,
        title="Email test",
        body="Body",
        payload={},
        status=NotificationStatus.PENDING,
        attempts=0,
        dedup_key="test",
        recipient_email="recipient@example.test",
        correlation_id=None,
        error=None,
        next_attempt_at=None,
        processing_started_at=None,
        created_at=datetime.now(UTC),
        sent_at=None,
        read_at=None,
    )


def test_email_enabled_requires_credentials() -> None:
    with pytest.raises(ValidationError):
        Settings(
            _env_file=None,
            email_enabled=True,
            smtp_username="",
            smtp_password="",
        )


def test_sender_address_falls_back_to_username() -> None:
    settings = make_settings(
        email_enabled=True,
        smtp_username="sender@gmail.com",
        smtp_password="app-password",
        smtp_from="",
    )
    assert settings.sender_address == "sender@gmail.com"


async def test_smtp_sender_uses_gmail_settings(monkeypatch) -> None:
    settings = make_settings(
        email_enabled=True,
        smtp_username="sender@gmail.com",
        smtp_password="app-password",
        smtp_from="",
    )
    captured: dict = {}

    async def fake_send(message, **kwargs):
        captured["message"] = message
        captured.update(kwargs)

    monkeypatch.setattr(
        "notification_service.infrastructure.channels.email.aiosmtplib.send",
        fake_send,
    )

    await SmtpEmailSender(settings).send(notification())

    assert captured["hostname"] == "smtp.gmail.com"
    assert captured["port"] == 587
    assert captured["start_tls"] is True
    assert captured["use_tls"] is False
    assert captured["username"] == "sender@gmail.com"
    assert captured["password"] == "app-password"
    assert "sender@gmail.com" in captured["message"]["From"]
    assert captured["message"]["To"] == "recipient@example.test"
