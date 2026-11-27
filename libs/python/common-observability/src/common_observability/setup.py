from __future__ import annotations

import logging
from collections.abc import Mapping

from fastapi import FastAPI

from .health import Check, health_router
from .logging import configure_logging
from .metrics import install_metrics
from .middleware import ObservabilityMiddleware


def setup_observability(
    app: FastAPI,
    *,
    service: str,
    environment: str = "dev",
    readiness_checks: Mapping[str, Check] | None = None,
    log_level: int = logging.INFO,
) -> None:
    configure_logging(service, environment, log_level)
    app.add_middleware(ObservabilityMiddleware)
    install_metrics(app, service)
    app.include_router(health_router(readiness_checks))
