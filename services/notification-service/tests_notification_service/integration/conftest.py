"""Testcontainers fixtures for notification-service persistence tests."""

from __future__ import annotations

import pytest
from common_db import Base
from sqlalchemy import text
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine
from sqlalchemy.pool import NullPool
from testcontainers.community.postgres import PostgresContainer
from testcontainers.core.container import DockerContainer
from testcontainers.core.wait_strategies import LogMessageWaitStrategy

from notification_service.infrastructure.db import models  # noqa: F401  (register metadata)


def _docker_available() -> bool:
    try:
        from testcontainers.core.docker_client import DockerClient

        DockerClient().client.ping()
    except Exception:
        return False
    return True


DOCKER_AVAILABLE = _docker_available()


@pytest.fixture(scope="session", autouse=True)
def require_docker() -> None:
    if not DOCKER_AVAILABLE:
        pytest.skip("Docker is required for integration tests (Testcontainers)")


@pytest.fixture(scope="session")
def postgres_container() -> PostgresContainer:
    with PostgresContainer(
        "postgres:17-alpine",
        username="notification",
        password="notification",
        dbname="notification",
        driver="asyncpg",
    ) as postgres:
        yield postgres


@pytest.fixture(scope="session")
def database_url(postgres_container: PostgresContainer) -> str:
    return postgres_container.get_connection_url()


@pytest.fixture(scope="session")
def rabbitmq_container():
    container = (
        DockerContainer("rabbitmq:4-management-alpine")
        .with_exposed_ports(5672)
        .waiting_for(LogMessageWaitStrategy("Server startup complete"))
    )
    with container:
        yield container


@pytest.fixture(scope="session")
def rabbitmq_url(rabbitmq_container) -> str:
    host = rabbitmq_container.get_container_host_ip()
    port = rabbitmq_container.get_exposed_port(5672)
    return f"amqp://guest:guest@{host}:{port}/"


@pytest.fixture
async def session_factory(database_url: str):
    engine = create_async_engine(database_url, poolclass=NullPool)
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
        await connection.execute(
            text(
                "TRUNCATE TABLE notification, notification_contact, "
                "outbox_event RESTART IDENTITY CASCADE"
            )
        )
    yield async_sessionmaker(engine, expire_on_commit=False)
    await engine.dispose()
