"""Video service logging setup (delegates to common-observability)."""

from __future__ import annotations

import logging

from common_observability import configure_logging as configure_structured_logging

from video_service.config import Settings


def configure_logging(settings: Settings) -> None:
    configure_structured_logging(
        "video-service",
        environment=settings.environment,
        level=settings.log_level,
    )
    logging.getLogger("uvicorn.access").disabled = True
    logging.getLogger("aiosqlite").setLevel(logging.WARNING)
