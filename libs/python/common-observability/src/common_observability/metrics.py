from __future__ import annotations

import time

from fastapi import FastAPI, Request, Response
from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint

REQUEST_COUNT = Counter(
    "http_requests_total",
    "HTTP requests processed by the service",
    ["service", "method", "path", "status"],
)
REQUEST_DURATION = Histogram(
    "http_request_duration_seconds",
    "HTTP request duration",
    ["service", "method", "path"],
)


class MetricsMiddleware(BaseHTTPMiddleware):
    def __init__(self, app, service: str) -> None:
        super().__init__(app)
        self._service = service

    async def dispatch(
        self,
        request: Request,
        call_next: RequestResponseEndpoint,
    ) -> Response:
        started = time.perf_counter()
        response = await call_next(request)
        route = request.scope.get("route")
        path = route.path if route is not None else request.url.path
        elapsed = time.perf_counter() - started
        labels = (self._service, request.method, path)
        REQUEST_COUNT.labels(*labels, str(response.status_code)).inc()
        REQUEST_DURATION.labels(*labels).observe(elapsed)
        return response


def install_metrics(app: FastAPI, service: str) -> None:
    app.add_middleware(MetricsMiddleware, service=service)

    @app.get("/metrics", include_in_schema=False)
    async def metrics() -> Response:
        return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)
