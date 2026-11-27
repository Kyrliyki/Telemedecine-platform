"""HTTP middleware: request-id logging context and Prometheus metrics."""

from __future__ import annotations

from time import perf_counter
from uuid import uuid4

import structlog
from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.responses import Response

from appointment.infrastructure.logging import get_logger

logger = get_logger(__name__)


class RequestContextMiddleware(BaseHTTPMiddleware):
    """Adds ``X-Request-Id`` and binds request information to every log line."""

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        request_id = request.headers.get("X-Request-Id") or uuid4().hex
        structlog.contextvars.clear_contextvars()
        structlog.contextvars.bind_contextvars(
            request_id=request_id,
            method=request.method,
            path=request.url.path,
        )
        started = perf_counter()

        try:
            response = await call_next(request)
        except Exception:
            logger.exception("request_failed")
            raise

        duration_ms = round((perf_counter() - started) * 1000, 2)
        response.headers["X-Request-Id"] = request_id
        logger.info(
            "request_completed",
            status=response.status_code,
            duration_ms=duration_ms,
        )
        return response
