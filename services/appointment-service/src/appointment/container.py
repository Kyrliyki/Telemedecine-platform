"""Composition root: wires adapters to use cases.

``build_container`` is the only place that knows about concrete infrastructure.
Tests replace the container with in-memory fakes, so no database is required for
unit/API tests.
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import cast

from sqlalchemy import text
from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from appointment.application.ports import (
    Cache,
    Clock,
    EventPublisher,
    ScheduleGateway,
    UserDirectoryGateway,
)
from appointment.application.use_cases import (
    CancelAppointment,
    CompleteAppointment,
    ConfirmAppointment,
    CreateAppointment,
    GetAppointment,
    ListAppointments,
    MarkNoShow,
    RescheduleAppointment,
)
from appointment.domain.ports import UnitOfWork
from appointment.infrastructure.cache import NullCache, RedisCache
from appointment.infrastructure.config import Settings
from appointment.infrastructure.db.uow import SqlAlchemyUnitOfWork
from appointment.infrastructure.logging import get_logger
from appointment.infrastructure.messaging import BrokerUnavailable, RabbitEventPublisher
from appointment.infrastructure.outbox_relay import OutboxRelay
from appointment.infrastructure.schedule import (
    HttpScheduleGateway,
    UnavailableScheduleGateway,
)
from appointment.infrastructure.security import JwtAuthenticator
from appointment.infrastructure.user_directory import (
    HttpUserDirectoryGateway,
    UnavailableUserDirectoryGateway,
)

logger = get_logger(__name__)

class SystemClock:
    def now(self) -> datetime:
        return datetime.now(UTC)

@dataclass
class Container:
    settings: Settings
    uow_factory: Callable[[], UnitOfWork]
    cache: Cache
    clock: Clock
    authenticator: JwtAuthenticator
    publisher: EventPublisher | None = None
    schedule: ScheduleGateway | None = None
    users: UserDirectoryGateway | None = None
    engine: AsyncEngine | None = None
    session_factory: async_sessionmaker[AsyncSession] | None = None
    relay: OutboxRelay | None = None
    managed: bool = False

    
    create_appointment: CreateAppointment = field(init=False)
    get_appointment: GetAppointment = field(init=False)
    list_appointments: ListAppointments = field(init=False)
    confirm_appointment: ConfirmAppointment = field(init=False)
    reschedule_appointment: RescheduleAppointment = field(init=False)
    cancel_appointment: CancelAppointment = field(init=False)
    complete_appointment: CompleteAppointment = field(init=False)
    mark_no_show: MarkNoShow = field(init=False)

    def __post_init__(self) -> None:
        factory = self.uow_factory
        cache = self.cache
        clock = self.clock
        schedule = self.schedule
        users = self.users
        self.create_appointment = CreateAppointment(factory, cache, clock, schedule, users)
        self.get_appointment = GetAppointment(factory, cache, clock)
        self.list_appointments = ListAppointments(factory, cache, clock)
        self.confirm_appointment = ConfirmAppointment(factory, cache, clock, schedule)
        self.reschedule_appointment = RescheduleAppointment(
            factory, cache, clock, schedule, users
        )
        self.cancel_appointment = CancelAppointment(factory, cache, clock, schedule)
        self.complete_appointment = CompleteAppointment(factory, cache, clock)
        self.mark_no_show = MarkNoShow(factory, cache, clock)

    async def startup(self) -> None:
        if self.settings.cache_enabled:
            healthy = await self.cache.ping()
            logger.info("cache_ready" if healthy else "cache_unavailable")
        if self.publisher is not None:
            try:
                await self.publisher.connect()
            except BrokerUnavailable:
                logger.warning("broker_unavailable_on_startup")
        if self.relay is not None:
            await self.relay.start()

    async def shutdown(self) -> None:
        if self.relay is not None:
            await self.relay.stop()
        if self.publisher is not None:
            await self.publisher.close()
        if self.schedule is not None:
            await self.schedule.aclose()
        if self.users is not None:
            await self.users.aclose()
        await self.authenticator.aclose()
        await self.cache.close()
        if self.engine is not None:
            await self.engine.dispose()

    async def check_database(self) -> bool:
        if self.engine is None:
            return True
        async with self.engine.connect() as connection:
            await connection.execute(text("SELECT 1"))
        return True

    async def check_cache(self) -> bool:
        if not self.settings.cache_enabled:
            return True
        return await self.cache.ping()

    @property
    def broker_connected(self) -> bool:
        return self.publisher.connected if self.publisher is not None else False

def build_container(settings: Settings) -> Container:
    engine_kwargs: dict[str, object] = {
        "echo": settings.db_echo,
        "pool_pre_ping": True,
    }
    if settings.database_url.startswith("postgresql"):
        engine_kwargs["pool_size"] = settings.db_pool_size
        engine_kwargs["max_overflow"] = settings.db_max_overflow
    if settings.db_schema:
        
        engine_kwargs["connect_args"] = {
            "options": f"-c search_path={settings.db_schema}",
        }

    engine = create_async_engine(settings.database_url, **engine_kwargs)
    session_factory = async_sessionmaker(engine, expire_on_commit=False)

    cache: Cache
    if settings.cache_enabled:
        cache = RedisCache.from_url(settings.redis_url, default_ttl=settings.cache_ttl_seconds)
    else:
        cache = NullCache()

    publisher: RabbitEventPublisher | None = None
    relay: OutboxRelay | None = None
    if settings.broker_enabled:
        publisher = RabbitEventPublisher(settings.rabbitmq_url, settings.rabbitmq_exchange)
        relay = OutboxRelay(
            session_factory,
            publisher,
            interval_seconds=settings.outbox_poll_interval,
            batch_size=settings.outbox_batch_size,
        )

    schedule: ScheduleGateway
    if settings.schedule_service_url:
        schedule = HttpScheduleGateway(
            base_url=settings.schedule_service_url,
            internal_token=settings.internal_service_token,
        )
    else:
        schedule = UnavailableScheduleGateway()

    users: UserDirectoryGateway
    if settings.user_service_url:
        users = HttpUserDirectoryGateway(
            base_url=settings.user_service_url,
            internal_token=settings.internal_service_token,
        )
    else:
        users = UnavailableUserDirectoryGateway()

    return Container(
        settings=settings,
        uow_factory=lambda: cast(UnitOfWork, SqlAlchemyUnitOfWork(session_factory)),
        cache=cache,
        clock=SystemClock(),
        authenticator=JwtAuthenticator(settings),
        publisher=publisher,
        schedule=schedule,
        users=users,
        engine=engine,
        session_factory=session_factory,
        relay=relay,
        managed=True,
    )
