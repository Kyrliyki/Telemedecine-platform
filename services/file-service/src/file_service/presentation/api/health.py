"""Health probes."""

from __future__ import annotations

from typing import Annotated, Any

from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse

from file_service.presentation.api.deps import ContainerProtocol, get_container

router = APIRouter(tags=["health"])


@router.get("/health/live", summary="Liveness probe")
async def liveness() -> dict[str, str]:
    return {"status": "ok"}


@router.get("/health/ready", summary="Readiness probe with dependency checks")
async def readiness(
    container: Annotated[ContainerProtocol, Depends(get_container)],
) -> JSONResponse:
    result: dict[str, Any] = await container.health()
    status_code = 200 if result.get("status") == "ok" else 503
    return JSONResponse(status_code=status_code, content=result)
