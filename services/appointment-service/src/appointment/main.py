"""FastAPI application factory and local entry point."""

from __future__ import annotations

import os
from contextlib import asynccontextmanager

from common_observability import install_metrics
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, RedirectResponse

from appointment.container import Container, build_container
from appointment.infrastructure.config import Settings, get_settings
from appointment.infrastructure.db.migrations import run_migrations
from appointment.infrastructure.logging import configure_logging
from appointment.presentation.errors import register_exception_handlers
from appointment.presentation.health import router as health_router
from appointment.presentation.internal import router as internal_router
from appointment.presentation.middleware import RequestContextMiddleware
from appointment.presentation.routes import router as appointments_router

OPENAPI_TAGS = [
    {
        "name": "appointments",
        "description": "Booking, confirmation, rescheduling and cancellation of consultations.",
    },
    {"name": "health", "description": "Kubernetes/Docker probes."},
]


def create_app(
    settings: Settings | None = None,
    container: Container | None = None,
) -> FastAPI:
    settings = settings or get_settings()
    configure_logging(
        settings.log_level,
        json_logs=not settings.is_dev,
        environment=settings.env,
    )
    active_container = container or build_container(settings)

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        if active_container.managed:
            if settings.auto_migrate:
                await run_migrations(settings.database_url, settings.alembic_ini)
            await active_container.startup()
        try:
            yield
        finally:
            if active_container.managed:
                await active_container.shutdown()

    app = FastAPI(
        title="Appointment Service",
        version=settings.version,
        summary="Scheduling and booking service of the Telemed telemedicine platform",
        description=(
            "Microservice responsible for consultation slots: booking, confirmation, "
            "rescheduling and cancellation.\n\n"
            "**Auth**: send `Authorization: Bearer <JWT>` (token issued by AuthService). "
            "Roles: `patient`, `doctor`, `admin`.\n\n"
            "**Events**: every state change is written to the transactional outbox and "
            "published to RabbitMQ (`appointment.*` routing keys)."
        ),
        openapi_tags=OPENAPI_TAGS,
        lifespan=lifespan,
        docs_url="/docs",
        redoc_url="/redoc",
        openapi_url="/openapi.json",
    )

    app.state.container = active_container
    app.state.settings = settings

    app.add_middleware(RequestContextMiddleware)
    if settings.cors_origin_list:
        app.add_middleware(
            CORSMiddleware,
            allow_origins=settings.cors_origin_list,
            allow_credentials=False,
            allow_methods=["*"],
            allow_headers=["*"],
            expose_headers=["X-Request-Id"],
        )

    register_exception_handlers(app)

    app.include_router(appointments_router, prefix="/api/v1")
    app.include_router(health_router)
    app.include_router(internal_router)

    @app.get("/v3/api-docs", include_in_schema=False)
    async def openapi_alias() -> JSONResponse:
        """Springdoc-compatible alias used by the Java API Gateway."""
        return JSONResponse(app.openapi())

    install_metrics(app, "appointment-service")

    @app.get("/", include_in_schema=False)
    async def root() -> RedirectResponse:
        return RedirectResponse(url="/docs")

    return app


app = create_app()


def run() -> None:
    """``python main.py`` / ``appointment-service`` entry point."""
    import uvicorn

    uvicorn.run(
        "appointment.main:app",
        host=os.getenv("APP_HOST", "0.0.0.0"),
        port=int(os.getenv("APP_PORT", "8000")),
        reload=os.getenv("APP_ENV", "dev") == "dev",
    )


if __name__ == "__main__":
    run()
