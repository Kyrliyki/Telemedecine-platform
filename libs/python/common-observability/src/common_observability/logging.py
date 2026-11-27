from __future__ import annotations

import logging
import sys

import structlog


def _normalize_level(level: int | str) -> int:
    if isinstance(level, str):
        return getattr(logging, level.upper(), logging.INFO)
    return level


def get_logger(name: str | None = None) -> structlog.stdlib.BoundLogger:
    """Return a structlog logger that is routed through the configured handler."""
    return structlog.get_logger(name)


def configure_logging(
    service: str,
    environment: str = "dev",
    level: int | str = logging.INFO,
    *,
    json_logs: bool = True,
) -> None:
    """Configure structlog-backed logging for the standard library too.

    Both ``structlog.get_logger(...)`` and plain ``logging.getLogger(...)`` calls
    are rendered through ``ProcessorFormatter``, so existing code keeps working
    while gaining structured context (service, environment, request id).
    """

    numeric_level = _normalize_level(level)

    structlog.configure(
        processors=[
            structlog.contextvars.merge_contextvars,
            structlog.stdlib.add_log_level,
            structlog.stdlib.add_logger_name,
            structlog.processors.TimeStamper(fmt="iso", utc=True),
            structlog.processors.StackInfoRenderer(),
            structlog.processors.format_exc_info,
            structlog.stdlib.ProcessorFormatter.wrap_for_formatter,
        ],
        logger_factory=structlog.stdlib.LoggerFactory(),
        wrapper_class=structlog.stdlib.BoundLogger,
        cache_logger_on_first_use=True,
    )

    if json_logs:
        renderer_processors: list[structlog.types.Processor] = [
            structlog.stdlib.ProcessorFormatter.remove_processors_meta,
            structlog.processors.EventRenamer("message"),
            structlog.processors.JSONRenderer(ensure_ascii=False, default=str),
        ]
    else:
        renderer_processors = [
            structlog.stdlib.ProcessorFormatter.remove_processors_meta,
            structlog.dev.ConsoleRenderer(colors=sys.stdout.isatty()),
        ]

    formatter = structlog.stdlib.ProcessorFormatter(
        foreign_pre_chain=[
            structlog.contextvars.merge_contextvars,
            structlog.stdlib.add_log_level,
            structlog.stdlib.add_logger_name,
            structlog.processors.TimeStamper(fmt="iso", utc=True),
            structlog.processors.format_exc_info,
            structlog.stdlib.ExtraAdder(),
        ],
        processors=renderer_processors,
    )

    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(formatter)

    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(numeric_level)

    structlog.contextvars.bind_contextvars(
        service=service,
        environment=environment,
    )
