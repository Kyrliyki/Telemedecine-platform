"""Event routing by ``eventType``.

A service subscribes to a routing key in RabbitMQ, then dispatches each message
to a Python handler. ``EventRouter`` keeps that mapping in one place and
supports exact names as well as ``*``/``#`` glob patterns.
"""

from __future__ import annotations

import fnmatch
import logging
from collections.abc import Awaitable, Callable

from common_events import EventEnvelope

logger = logging.getLogger(__name__)

EventHandler = Callable[[EventEnvelope], Awaitable[None]]


class EventRouter:
    """Maps event types to async handlers.

    ``*`` matches any characters and ``#`` is treated as an alias for ``*``,
    so ``auth.user.#`` matches every auth user event.
    """

    def __init__(self, *, ignore_unknown: bool = True) -> None:
        self._handlers: dict[str, EventHandler] = {}
        self._ignore_unknown = ignore_unknown

    def register(self, event_type: str, handler: EventHandler) -> None:
        if event_type in self._handlers:
            raise ValueError(f"Handler already registered for {event_type}")
        self._handlers[event_type] = handler

    def on(self, event_type: str) -> Callable[[EventHandler], EventHandler]:
        """Decorator form of :meth:`register`."""

        def decorator(handler: EventHandler) -> EventHandler:
            self.register(event_type, handler)
            return handler

        return decorator

    def handles(self, event_type: str) -> bool:
        return self._resolve(event_type) is not None

    def _resolve(self, event_type: str) -> EventHandler | None:
        exact = self._handlers.get(event_type)
        if exact is not None:
            return exact
        for pattern, handler in self._handlers.items():
            if fnmatch.fnmatchcase(event_type, pattern.replace("#", "*")):
                return handler
        return None

    async def dispatch(self, envelope: EventEnvelope) -> bool:
        """Call the matching handler.

        Returns ``True`` when a handler ran. Unknown events are acknowledged
        and ignored by default, which keeps a service from blocking on events
        it does not consume yet.
        """

        handler = self._resolve(envelope.event_type)
        if handler is None:
            if not self._ignore_unknown:
                raise KeyError(f"No handler registered for {envelope.event_type}")
            logger.debug("Ignoring unhandled event type=%s", envelope.event_type)
            return False
        await handler(envelope)
        return True
