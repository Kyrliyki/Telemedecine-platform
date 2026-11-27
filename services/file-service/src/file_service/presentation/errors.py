"""HTTP exception handlers: one consistent error envelope."""

from __future__ import annotations

import logging
from typing import Any

from fastapi import FastAPI, Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from file_service.domain.exceptions import (
    AccessCheckUnavailableError,
    AuthenticationError,
    ConflictError,
    DomainError,
    FileTooLargeError,
    ForbiddenError,
    NotFoundError,
    StorageUnavailableError,
    ValidationError,
)

logger = logging.getLogger(__name__)

_STATUS_BY_ERROR: tuple[tuple[type[DomainError], int], ...] = (
    (AuthenticationError, 401),
    (FileTooLargeError, 413),
    (ValidationError, 422),
    (NotFoundError, 404),
    (ForbiddenError, 403),
    (ConflictError, 409),
    (StorageUnavailableError, 502),
    (AccessCheckUnavailableError, 503),
)


def _error_body(code: str, message: str, details: Any = None) -> dict[str, Any]:
    body: dict[str, Any] = {"error": {"code": code, "message": message}}
    if details is not None:
        body["error"]["details"] = details
    return body


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(DomainError)
    async def domain_error_handler(request: Request, exc: DomainError) -> JSONResponse:
        status_code = 400
        for error_type, mapped_status in _STATUS_BY_ERROR:
            if isinstance(exc, error_type):
                status_code = mapped_status
                break
        headers = {"WWW-Authenticate": "Bearer"} if status_code == 401 else None
        if status_code >= 500:
            logger.error("domain error: %s", exc, exc_info=True)
        return JSONResponse(
            status_code=status_code,
            content=_error_body(exc.code, exc.message),
            headers=headers,
        )

    @app.exception_handler(RequestValidationError)
    async def validation_error_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
        return JSONResponse(
            status_code=422,
            content=_error_body(
                "validation_error",
                "Request validation failed",
                jsonable_encoder(exc.errors()),
            ),
        )

    @app.exception_handler(StarletteHTTPException)
    async def http_error_handler(request: Request, exc: StarletteHTTPException) -> JSONResponse:
        detail = exc.detail if isinstance(exc.detail, str) else "HTTP error"
        return JSONResponse(
            status_code=exc.status_code,
            content=_error_body("http_error", detail),
            headers=exc.headers,
        )
