"""FastAPI application factory for NotificationService."""

from __future__ import annotations

import os
from contextlib import asynccontextmanager

from common_messaging import DlqQueueSpec, build_dlq_router
from common_observability import setup_observability
from common_web import install_cors, install_problem_details
from fastapi import FastAPI
from fastapi.responses import JSONResponse

from notification_service.config import Settings, get_settings
from notification_service.container import Container, build_container
from notification_service.infrastructure.auth import build_jwt_auth
from notification_service.presentation.routers import dev, notifications


def create_app(
    settings: Settings | None = None,
    container: Container | None = None,
) -> FastAPI:
    settings = settings or get_settings()
    active_container = container or build_container(settings)
    auth = build_jwt_auth(settings)

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        if active_container.managed:
            await active_container.start()
        try:
            yield
        finally:
            if active_container.managed:
                await active_container.stop()
            await auth.aclose()

    app = FastAPI(
        title="Notification Service",
        version="0.1.0",
        description=(
            "Уведомления Telemed: email, in-app, история и статусы доставки.\n\n"
            "Consumes: `auth.events`, `appointment.events`, `video.events`.\n\n"
            "Publishes: `notification.notification.sent.v1` в `notification.events`."
        ),
        lifespan=lifespan,
        openapi_url="/v3/api-docs",
        docs_url="/docs",
        redoc_url="/redoc",
    )

    app.state.container = active_container
    app.state.settings = settings
    app.state.auth = auth

    setup_observability(
        app,
        service=settings.service_name,
        environment=settings.environment,
        readiness_checks={
            "database": active_container.check_database,
            "redis": active_container.check_redis,
            "rabbitmq": active_container.check_rabbitmq,
        },
    )
    install_problem_details(app)
    install_cors(app, allowed_origins=settings.cors_origin_list)

    app.include_router(notifications.router, prefix=settings.api_prefix)
    if settings.dev_endpoints_enabled and settings.is_dev:
        app.include_router(dev.router, prefix=settings.api_prefix)
    app.include_router(
        build_dlq_router(
            rabbitmq_url=settings.rabbitmq_url,
            internal_token=settings.internal_service_token,
            specs=[
                DlqQueueSpec(
                    key="auth",
                    queue=f"{settings.rabbitmq_auth_queue}.dlq",
                    exchange=settings.rabbitmq_auth_exchange,
                    description="auth events",
                ),
                DlqQueueSpec(
                    key="user",
                    queue=f"{settings.rabbitmq_user_queue}.dlq",
                    exchange=settings.rabbitmq_user_exchange,
                    description="user events",
                ),
                DlqQueueSpec(
                    key="appointment",
                    queue=f"{settings.rabbitmq_appointment_queue}.dlq",
                    exchange=settings.rabbitmq_appointment_exchange,
                    description="appointment events",
                ),
                DlqQueueSpec(
                    key="video",
                    queue=f"{settings.rabbitmq_video_queue}.dlq",
                    exchange=settings.rabbitmq_video_exchange,
                    description="video events",
                ),
            ],
        )
    )

    @app.get("/openapi.json", include_in_schema=False)
    async def openapi_alias() -> JSONResponse:
        return JSONResponse(app.openapi())

    return app


app = create_app()


def run() -> None:
    import uvicorn

    uvicorn.run(
        "notification_service.main:app",
        host=os.getenv("HOST", "0.0.0.0"),
        port=int(os.getenv("SERVER_PORT", "8088")),
    )


if __name__ == "__main__":
    run()
