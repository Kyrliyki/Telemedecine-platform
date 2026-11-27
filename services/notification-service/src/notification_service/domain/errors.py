"""Domain errors."""

from __future__ import annotations


class NotificationError(Exception):
    pass


class NotificationNotFound(NotificationError):
    pass


class ForbiddenNotification(NotificationError):
    pass
