"""SMTP email delivery."""

from __future__ import annotations

import html
import logging
from email.message import EmailMessage

import aiosmtplib

from notification_service.config import Settings
from notification_service.domain.entities import Notification

logger = logging.getLogger(__name__)


class SmtpEmailSender:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def send(self, notification: Notification) -> None:
        if not notification.recipient_email:
            raise ValueError("email notification has no recipient address")

        recipients = [notification.recipient_email]
        sender = self._settings.sender_address
        if not sender:
            raise ValueError("SMTP_FROM or SMTP_USERNAME is not configured")

        message = EmailMessage()
        message["From"] = f"{self._settings.smtp_from_name} <{sender}>"
        message["To"] = ", ".join(recipients)
        message["Subject"] = notification.title
        message.set_content(notification.body)

        safe_body = html.escape(notification.body).replace("\n", "<br>\n")
        message.add_alternative(
            f"""<!doctype html>
<html lang="ru"><body style="font-family: Arial, sans-serif; color: #102a2a;">
  <div style="max-width: 560px; margin: 0 auto; padding: 24px;">
    <h2 style="color: #0f766e;">{html.escape(notification.title)}</h2>
    <p style="font-size: 15px; line-height: 1.55;">{safe_body}</p>
    <p style="margin-top: 24px; font-size: 12px; color: #667b79;">
      Telemed · это автоматическое сообщение, не отвечайте на него.
    </p>
  </div>
</body></html>""",
            subtype="html",
        )

        await aiosmtplib.send(
            message,
            hostname=self._settings.smtp_host,
            port=self._settings.smtp_port,
            username=self._settings.smtp_username or None,
            password=self._settings.smtp_password or None,
            use_tls=self._settings.smtp_use_tls,
            start_tls=self._settings.smtp_starttls,
            timeout=self._settings.smtp_timeout_seconds,
        )
        logger.info(
            "notification.email_sent id=%s kind=%s",
            notification.id,
            notification.kind,
        )
