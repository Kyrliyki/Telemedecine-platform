"""FastAPI router exposing the service-local DLQ admin contract."""

from __future__ import annotations

import hmac
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Query, Request, status

from .dlq import (
    DlqAdminService,
    DlqNotFoundError,
    DlqPurgeResult,
    DlqQueueView,
    DlqReplayResult,
    DlqUnavailableError,
)


def create_dlq_router(
    service: DlqAdminService,
    internal_token: str,
    *,
    header_name: str = "X-Internal-Token",
    prefix: str = "/internal/dlq",
) -> APIRouter:
    """Create the internal DLQ router for a Python service.

    The token check is fail-closed: an empty ``internal_token`` rejects every
    request. The API Gateway never exposes ``/internal/**`` directly.
    """

    router = APIRouter(prefix=prefix, tags=["internal"])

    def authorize(request: Request) -> None:
        provided = request.headers.get(header_name)
        if not internal_token or provided is None or not hmac.compare_digest(
            internal_token, provided
        ):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid internal service token",
            )

    @router.get("", response_model=list[DlqQueueView], dependencies=[Depends(authorize)])
    async def list_queues() -> list[DlqQueueView]:
        return await service.list()

    @router.post(
        "/{key}/replay",
        response_model=DlqReplayResult,
        dependencies=[Depends(authorize)],
    )
    async def replay(
        key: str,
        limit: Annotated[int, Query(ge=1, le=1000)] = 100,
        dry_run: Annotated[bool, Query(alias="dryRun")] = False,
    ) -> DlqReplayResult:
        try:
            return await service.replay(key, limit=limit, dry_run=dry_run)
        except DlqNotFoundError as exception:
            raise HTTPException(status_code=404, detail=str(exception)) from exception
        except DlqUnavailableError as exception:
            raise HTTPException(status_code=503, detail=str(exception)) from exception

    @router.post(
        "/{key}/purge",
        response_model=DlqPurgeResult,
        dependencies=[Depends(authorize)],
    )
    async def purge(key: str) -> DlqPurgeResult:
        try:
            return await service.purge(key)
        except DlqNotFoundError as exception:
            raise HTTPException(status_code=404, detail=str(exception)) from exception
        except DlqUnavailableError as exception:
            raise HTTPException(status_code=503, detail=str(exception)) from exception

    return router
