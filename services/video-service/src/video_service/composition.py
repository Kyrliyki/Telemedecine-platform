"""Composition root: единственное место, где слои соединяются вместе."""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import timedelta

from sqlalchemy.ext.asyncio import AsyncEngine

from video_service.application.ports import (
    AppointmentDirectory,
    CallNotifier,
    EventPublisher,
    LiveKitGateway,
    TokenVerifier,
    UnitOfWork,
    UnitOfWorkFactory,
    WebhookParser,
)
from video_service.application.use_cases import (
    CreateCallUseCase,
    EndCallUseCase,
    GetCallUseCase,
    HandleLiveKitEventUseCase,
    JoinCallUseCase,
    ListCallsUseCase,
)
from video_service.config import Settings
from video_service.infrastructure.appointment_directory import (
    HttpAppointmentDirectory,
    UnavailableAppointmentDirectory,
)
from video_service.infrastructure.auth.jwt import JwtVerifier
from video_service.infrastructure.clock import SystemClock, Uuid4Generator
from video_service.infrastructure.livekit.gateway import LiveKitGatewayAdapter
from video_service.infrastructure.livekit.webhook import LiveKitWebhookParser
from video_service.infrastructure.logging import configure_logging
from video_service.infrastructure.messaging.logging_publisher import LoggingEventPublisher
from video_service.infrastructure.messaging.rabbitmq_publisher import RabbitMQEventPublisher
from video_service.infrastructure.notifications import RedisCallNotifier
from video_service.infrastructure.persistence.memory import InMemoryCallStore, InMemoryUnitOfWork
from video_service.infrastructure.persistence.postgres.database import (
    create_engine,
    create_session_factory,
    ping,
)
from video_service.infrastructure.persistence.postgres.uow import SqlAlchemyUnitOfWork

logger = logging.getLogger(__name__)


@dataclass
class Container:
    """Собранные зависимости приложения."""

    settings: Settings
    create_call: CreateCallUseCase
    join_call: JoinCallUseCase
    end_call: EndCallUseCase
    get_call: GetCallUseCase
    list_calls: ListCallsUseCase
    handle_livekit_event: HandleLiveKitEventUseCase
    livekit: LiveKitGateway
    publisher: EventPublisher
    webhook_parser: WebhookParser
    token_verifier: TokenVerifier
    notifier: CallNotifier | None = field(default=None, repr=False)
    appointments: AppointmentDirectory | None = field(default=None, repr=False)
    engine: AsyncEngine | None = field(default=None, repr=False)

    async def shutdown(self) -> None:
        aclose = getattr(self.livekit, "aclose", None)
        if aclose is not None:
            await aclose()
        publisher_close = getattr(self.publisher, "aclose", None)
        if publisher_close is not None:
            await publisher_close()
        notifier_close = getattr(self.notifier, "aclose", None)
        if notifier_close is not None:
            await notifier_close()
        appointments_close = getattr(self.appointments, "aclose", None)
        if appointments_close is not None:
            await appointments_close()
        if self.engine is not None:
            await self.engine.dispose()

    async def readiness(self) -> dict[str, bool]:
        checks = {"livekit": await self.livekit.healthcheck()}
        if self.engine is not None:
            checks["database"] = await ping(self.engine)
        return checks


def build_container(settings: Settings) -> Container:
    configure_logging(settings)

    clock = SystemClock()
    ids = Uuid4Generator()
    publisher: EventPublisher = (
        RabbitMQEventPublisher(
            url=settings.rabbitmq_url,
            exchange_name=settings.rabbitmq_exchange,
        )
        if settings.rabbitmq_url
        else LoggingEventPublisher()
    )
    engine: AsyncEngine | None = None

    if settings.storage_backend == "postgres":
        engine = create_engine(
            settings.database_url,
            echo=settings.database_echo,
            schema=settings.db_schema,
        )
        session_factory = create_session_factory(engine)

        def _sqlalchemy_uow() -> UnitOfWork:
            return SqlAlchemyUnitOfWork(session_factory)

        factory: UnitOfWorkFactory = _sqlalchemy_uow
    else:
        store = InMemoryCallStore()

        def _memory_uow() -> UnitOfWork:
            return InMemoryUnitOfWork(store)

        factory = _memory_uow

    livekit = LiveKitGatewayAdapter(
        url=settings.livekit_url,
        api_key=settings.livekit_api_key,
        api_secret=settings.livekit_api_secret_value,
        empty_timeout_seconds=settings.livekit_room_empty_timeout_seconds,
        departure_timeout_seconds=settings.livekit_departure_timeout_seconds,
    )
    notifier: CallNotifier | None = (
        RedisCallNotifier(settings.redis_url) if settings.redis_url else None
    )
    appointments: AppointmentDirectory = (
        HttpAppointmentDirectory(
            base_url=settings.appointment_service_url,
            internal_token=settings.internal_service_token,
        )
        if settings.appointment_service_url
        else UnavailableAppointmentDirectory()
    )
    token_ttl = timedelta(seconds=settings.livekit_token_ttl_seconds)

    return Container(
        settings=settings,
        create_call=CreateCallUseCase(
            uow_factory=factory,
            livekit=livekit,
            clock=clock,
            ids=ids,
            publisher=publisher,
            notifier=notifier,
            appointments=appointments,
        ),
        join_call=JoinCallUseCase(
            uow_factory=factory,
            livekit=livekit,
            clock=clock,
            token_ttl=token_ttl,
            public_url=settings.livekit_public_url,
        ),
        end_call=EndCallUseCase(
            uow_factory=factory,
            livekit=livekit,
            clock=clock,
            publisher=publisher,
        ),
        get_call=GetCallUseCase(uow_factory=factory),
        list_calls=ListCallsUseCase(uow_factory=factory),
        handle_livekit_event=HandleLiveKitEventUseCase(
            uow_factory=factory,
            clock=clock,
            publisher=publisher,
        ),
        livekit=livekit,
        publisher=publisher,
        webhook_parser=LiveKitWebhookParser(
            api_key=settings.livekit_api_key,
            api_secret=settings.livekit_api_secret_value,
        ),
        notifier=notifier,
        appointments=appointments,
        token_verifier=JwtVerifier(
            secret=settings.jwt_secret_value,
            algorithms=[settings.jwt_algorithm],
            audience=settings.jwt_audience_or_none,
            issuer=settings.jwt_issuer_or_none,
            public_key=settings.jwt_public_key,
            jwks_url=settings.jwt_jwks_url,
            auth_redis_url=settings.auth_redis_url,
            revocation_enabled=settings.auth_revocation_enabled,
        ),
        engine=engine,
    )
