"""Сборка FastAPI-приложения."""

from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from common_observability import install_metrics
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from video_service.composition import Container, build_container
from video_service.config import Settings, get_settings
from video_service.presentation.errors import register_error_handlers
from video_service.presentation.middleware import ObservabilityMiddleware
from video_service.presentation.routers import calls, health, webhooks


def create_app(
    container: Container | None = None,
    settings: Settings | None = None,
) -> FastAPI:
    settings = settings or (container.settings if container else get_settings())
    container = container or build_container(settings)

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        try:
            yield
        finally:
            await container.shutdown()

    app = FastAPI(
        title=settings.app_name,
        version="0.1.0",
        description=(
            "Микросервис видеоконсультаций: комнаты LiveKit, токены участников, "
            "жизненный цикл звонка и вебхуки LiveKit."
        ),
        lifespan=lifespan,
        docs_url="/docs" if settings.docs_enabled else None,
        redoc_url="/redoc" if settings.docs_enabled else None,
        openapi_url="/openapi.json" if settings.docs_enabled else None,
    )
    app.state.container = container

    app.add_middleware(ObservabilityMiddleware)
    if settings.cors_origin_list:
        app.add_middleware(
            CORSMiddleware,
            allow_origins=settings.cors_origin_list,
            allow_credentials=True,
            allow_methods=["*"],
            allow_headers=["*"],
        )

    register_error_handlers(app)
    
    
    app.include_router(calls.router, prefix="/api/v1/calls")
    app.include_router(calls.router, prefix="/api/v1/video")
    app.include_router(webhooks.router)
    app.include_router(health.router)
    install_metrics(app, "video-service")

    @app.get("/v3/api-docs", include_in_schema=False)
    async def openapi_alias() -> JSONResponse:
        """Springdoc-compatible alias used by the Java API Gateway."""
        return JSONResponse(app.openapi())

    return app
