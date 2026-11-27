"""FastAPI application factory."""

from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from common_messaging import DlqQueueSpec, build_dlq_router
from common_observability import configure_logging, install_metrics
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from file_service import __version__
from file_service.bootstrap.container import Container
from file_service.infrastructure.config import Settings
from file_service.presentation.api import files, health, internal
from file_service.presentation.errors import register_exception_handlers
from file_service.presentation.middleware import ObservabilityMiddleware


def create_app(
    settings: Settings | None = None,
    container: Container | None = None,
) -> FastAPI:
    settings = settings or Settings()
    configure_logging(
        "file-service",
        environment=settings.app_env,
        level=settings.log_level,
        json_logs=settings.log_json,
    )

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        await app.state.container.start()
        try:
            yield
        finally:
            await app.state.container.stop()

    app = FastAPI(
        title="FileService API",
        version=__version__,
        description=(
            "Chat attachments for the telemedicine platform.\n\n"
            "**Upload modes**\n"
            "1. `POST /api/v1/chats/{chat_id}/files` - streamed multipart upload.\n"
            "2. `POST /api/v1/chats/{chat_id}/uploads` + presigned PUT + "
            "`POST /api/v1/files/{id}/complete` - direct-to-MinIO upload for large files.\n\n"
            "Files are private: the API returns short-lived presigned download URLs."
        ),
        lifespan=lifespan,
        openapi_url="/openapi.json",
        docs_url="/docs",
        redoc_url="/redoc",
        contact={"name": "Telemed platform", "email": "platform@telemed.local"},
        openapi_tags=[
            {"name": "files", "description": "Upload, history and download of chat attachments"},
            {"name": "health", "description": "Liveness and readiness probes"},
        ],
    )

    app.state.settings = settings
    app.state.container = container or Container(settings)

    app.add_middleware(ObservabilityMiddleware)
    if settings.cors_origin_list:
        origins = settings.cors_origin_list
        app.add_middleware(
            CORSMiddleware,
            allow_origins=origins,
            allow_credentials="*" not in origins,
            allow_methods=["*"],
            allow_headers=["*"],
            expose_headers=["X-Request-ID"],
        )

    register_exception_handlers(app)
    app.include_router(health.router)
    app.include_router(files.router, prefix=settings.api_prefix)
    app.include_router(internal.router)
    app.include_router(
        build_dlq_router(
            rabbitmq_url=settings.rabbitmq_url,
            internal_token=settings.internal_api_token,
            specs=[
                DlqQueueSpec(
                    key="chat-events",
                    queue=f"{settings.rabbitmq_consumer_queue}.dlq",
                    exchange=settings.rabbitmq_chat_events_exchange,
                    description="chat events",
                )
            ],
        )
    )

    @app.get("/v3/api-docs", include_in_schema=False)
    async def openapi_alias() -> JSONResponse:
        """Springdoc-compatible alias used by the Java API Gateway."""
        return JSONResponse(app.openapi())

    install_metrics(app, "file-service")

    return app


app = create_app()
