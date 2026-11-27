"""Structured logging configuration (delegates to common-observability)."""

from __future__ import annotations

import logging

from common_observability import configure_logging as configure_structured_logging
from common_observability import get_logger

__all__ = ["configure_logging", "get_logger"]


def configure_logging(
    level: str = "INFO",
    *,
    json_logs: bool = False,
    environment: str = "dev",
) -> None:
    configure_structured_logging(
        "appointment-service",
        environment=environment,
        level=level,
        json_logs=json_logs,
    )
    logging.getLogger("uvicorn.access").setLevel(logging.WARNING)
