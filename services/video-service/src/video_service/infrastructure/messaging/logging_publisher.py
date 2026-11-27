"""Публикация доменных событий в лог.

В production этот адаптер заменяется на RabbitMQ + Outbox Pattern:
событие сохраняется в БД в транзакции use case, а отдельный relay-процесс
публикует его в exchange ``video.events``.
"""

from __future__ import annotations

import logging

from video_service.domain.events import DomainEvent

logger = logging.getLogger("video_service.events")


class LoggingEventPublisher:
    async def publish(self, event: DomainEvent) -> None:
        logger.info(
            "domain_event",
            extra={"event": event.to_payload()},
        )

    async def aclose(self) -> None:
        return None
