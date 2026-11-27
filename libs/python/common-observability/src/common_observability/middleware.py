from __future__ import annotations

import uuid

import structlog
from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint

from .context import (
    RequestContext,
    reset_request_context,
    set_request_context,
)

logger = structlog.get_logger(__name__)


def _new_id() -> str:
    return str(uuid.uuid4())


class ObservabilityMiddleware(BaseHTTPMiddleware):
    async def dispatch(
        self,
        request: Request,
        call_next: RequestResponseEndpoint,
    ) -> Response:
        request_id = request.headers.get("X-Request-Id") or _new_id()
        correlation_id = request.headers.get("X-Correlation-Id") or _new_id()

        context = RequestContext(
            request_id=request_id,
            correlation_id=correlation_id,
        )
        token = set_request_context(context)
        structlog.contextvars.bind_contextvars(
            request_id=request_id,
            correlation_id=correlation_id,
        )

        try:
            response = await call_next(request)
            response.headers["X-Request-Id"] = request_id
            response.headers["X-Correlation-Id"] = correlation_id
            return response
        finally:
            structlog.contextvars.unbind_contextvars(
                "request_id", "correlation_id"
            )
            reset_request_context(token)
