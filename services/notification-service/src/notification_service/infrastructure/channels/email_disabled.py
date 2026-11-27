"""Email sender used when email delivery is disabled."""

from __future__ import annotations

from notification_service.domain.entities import Notification


class DisabledEmailSender:
    async def send(self, notification: Notification) -> None:
        raise RuntimeError("email delivery is disabled")
