from __future__ import annotations

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

logger = logging.getLogger(__name__)


def _problem(
    *,
    status: int,
    code: str,
    message: str,
    path: str,
    errors: list[dict] | None = None,
) -> JSONResponse:
    content = {
        "status": status,
        "code": code,
        "message": message,
        "path": path,
    }
    if errors:
        content["errors"] = errors
    return JSONResponse(
        status_code=status,
        content=content,
        media_type="application/problem+json",
    )


def install_problem_details(app: FastAPI) -> None:
    @app.exception_handler(StarletteHTTPException)
    async def http_exception_handler(
        request: Request,
        exception: StarletteHTTPException,
    ) -> JSONResponse:
        return _problem(
            status=exception.status_code,
            code=f"HTTP_{exception.status_code}",
            message=str(exception.detail),
            path=request.url.path,
        )

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(
        request: Request,
        exception: RequestValidationError,
    ) -> JSONResponse:
        errors = []
        for error in exception.errors():
            errors.append(
                {
                    "location": list(error.get("loc", [])),
                    "message": error.get("msg", "invalid value"),
                    "type": error.get("type", "validation_error"),
                }
            )
        return _problem(
            status=400,
            code="VALIDATION_ERROR",
            message="Request validation failed",
            path=request.url.path,
            errors=errors,
        )

    @app.exception_handler(Exception)
    async def unexpected_exception_handler(
        request: Request,
        exception: Exception,
    ) -> JSONResponse:
        logger.exception("Unhandled request error")
        return _problem(
            status=500,
            code="INTERNAL_ERROR",
            message="Internal server error",
            path=request.url.path,
        )
