from __future__ import annotations

import inspect
from collections.abc import Awaitable, Callable, Mapping

from fastapi import APIRouter
from fastapi.responses import JSONResponse

Check = Callable[[], bool | Awaitable[bool]]


def health_router(checks: Mapping[str, Check] | None = None) -> APIRouter:
    router = APIRouter(tags=["health"])
    readiness = dict(checks or {})

    @router.get("/health/live")
    async def live() -> dict[str, str]:
        return {"status": "UP"}

    @router.get("/health/ready")
    async def ready() -> JSONResponse:
        results: dict[str, str] = {}
        healthy = True
        for name, check in readiness.items():
            try:
                value = check()
                if inspect.isawaitable(value):
                    value = await value
                ok = bool(value)
            except Exception:
                ok = False
            results[name] = "UP" if ok else "DOWN"
            healthy = healthy and ok
        return JSONResponse(
            status_code=200 if healthy else 503,
            content={"status": "UP" if healthy else "DOWN", "components": results},
        )

    return router
