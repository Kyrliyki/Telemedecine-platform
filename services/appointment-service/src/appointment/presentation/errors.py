"""Consistent JSON error envelopes."""

from __future__ import annotations

import structlog
from fastapi import FastAPI, Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from appointment.domain.errors import DomainError
from appointment.infrastructure.logging import get_logger

logger = get_logger(__name__)


def _request_id() -> str | None:
    value = structlog.contextvars.get_contextvars().get("request_id")
    return str(value) if value is not None else None


def error_response(
    *,
    status_code: int,
    code: str,
    message: str,
    details: object | None = None,
    headers: dict[str, str] | None = None,
) -> JSONResponse:
    body: dict[str, object] = {
        "error": {
            "code": code,
            "message": message,
            "request_id": _request_id(),
        }
    }
    if details is not None:
        body["error"]["details"] = jsonable_encoder(details)  # type: ignore[index]
    return JSONResponse(status_code=status_code, content=body, headers=headers)


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(DomainError)
    async def _domain_error_handler(request: Request, exc: DomainError) -> JSONResponse:
        headers = {"WWW-Authenticate": "Bearer"} if exc.status_code == 401 else None
        log = logger.warning if exc.status_code < 500 else logger.error
        log("domain_error", code=exc.code, message=exc.message, path=request.url.path)
        return error_response(
            status_code=exc.status_code,
            code=exc.code,
            message=exc.message,
            headers=headers,
        )

    @app.exception_handler(RequestValidationError)
    async def _validation_error_handler(
        request: Request, exc: RequestValidationError
    ) -> JSONResponse:
        return error_response(
            status_code=422,
            code="validation_error",
            message="request validation failed",
            details=exc.errors(),
        )

    @app.exception_handler(Exception)
    async def _unhandled_error_handler(request: Request, exc: Exception) -> JSONResponse:
        logger.exception("unhandled_error", path=request.url.path)
        return error_response(
            status_code=500,
            code="internal_error",
            message="internal server error",
        )
