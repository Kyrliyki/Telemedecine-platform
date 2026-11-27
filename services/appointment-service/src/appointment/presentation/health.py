"""Liveness and readiness probes."""

from __future__ import annotations

from fastapi import APIRouter
from fastapi.responses import JSONResponse

from appointment.infrastructure.logging import get_logger
from appointment.presentation.deps import ContainerDep

logger = get_logger(__name__)
router = APIRouter(tags=["health"])


@router.get("/health/live", summary="Liveness probe")
async def liveness() -> dict[str, str]:
    return {"status": "ok"}


@router.get("/health/ready", summary="Readiness probe")
async def readiness(container: ContainerDep) -> JSONResponse:
    settings = container.settings
    checks: dict[str, str] = {}
    database_ok = True

    try:
        await container.check_database()
        checks["database"] = "ok"
    except Exception:
        logger.exception("readiness_database_check_failed")
        checks["database"] = "error"
        database_ok = False

    if settings.cache_enabled:
        checks["redis"] = "ok" if await container.check_cache() else "unavailable"
    else:
        checks["redis"] = "disabled"

    if container.publisher is not None:
        checks["rabbitmq"] = "ok" if container.broker_connected else "unavailable"
    else:
        checks["rabbitmq"] = "disabled"

    degraded = any(value == "unavailable" for value in checks.values())
    if not database_ok:
        status = "error"
    elif degraded:
        status = "degraded"
    else:
        status = "ok"

    return JSONResponse(
        status_code=200 if database_ok else 503,
        content={
            "status": status,
            "service": settings.app_name,
            "version": settings.version,
            "checks": checks,
        },
    )
