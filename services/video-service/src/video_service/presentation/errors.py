"""Единая обработка ошибок API в формате ``{"error": {...}}``."""

from __future__ import annotations

import logging
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from video_service.application.context import request_id_ctx
from video_service.application.ports import (
    LiveKitGatewayError,
    WebhookVerificationError,
)
from video_service.domain.errors import DomainError

logger = logging.getLogger(__name__)


def _payload(code: str, message: str, details: Any = None) -> dict[str, Any]:
    error: dict[str, Any] = {
        "code": code,
        "message": message,
        "request_id": request_id_ctx.get(),
    }
    if details is not None:
        error["details"] = details
    return {"error": error}


def register_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(DomainError)
    async def _domain_error(_: Request, exc: DomainError) -> JSONResponse:
        return JSONResponse(
            status_code=exc.http_status,
            content=_payload(exc.code, exc.message),
        )

    @app.exception_handler(LiveKitGatewayError)
    async def _livekit_error(_: Request, exc: LiveKitGatewayError) -> JSONResponse:
        logger.warning("livekit.error", exc_info=exc)
        return JSONResponse(
            status_code=503,
            content=_payload("livekit_unavailable", "Сервис видеозвонков недоступен"),
        )

    @app.exception_handler(WebhookVerificationError)
    async def _webhook_error(_: Request, exc: WebhookVerificationError) -> JSONResponse:
        return JSONResponse(
            status_code=401,
            content=_payload("invalid_webhook_signature", str(exc)),
        )

    @app.exception_handler(StarletteHTTPException)
    async def _http_error(_: Request, exc: StarletteHTTPException) -> JSONResponse:
        message = exc.detail if isinstance(exc.detail, str) else "Ошибка запроса"
        response = JSONResponse(
            status_code=exc.status_code,
            content=_payload("http_error", message),
        )
        if exc.headers:
            response.headers.update(exc.headers)
        return response

    @app.exception_handler(RequestValidationError)
    async def _validation_error(_: Request, exc: RequestValidationError) -> JSONResponse:
        details = [
            {
                "loc": ".".join(str(part) for part in error["loc"]),
                "message": error["msg"],
            }
            for error in exc.errors()
        ]
        return JSONResponse(
            status_code=422,
            content=_payload("validation_error", "Некорректный запрос", details),
        )

    @app.exception_handler(Exception)
    async def _unhandled_error(_: Request, exc: Exception) -> JSONResponse:
        logger.exception("http.unhandled_error")
        return JSONResponse(
            status_code=500,
            content=_payload("internal_error", "Внутренняя ошибка сервиса"),
        )
