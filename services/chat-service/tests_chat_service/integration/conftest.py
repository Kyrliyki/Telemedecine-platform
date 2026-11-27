"""Testcontainers fixtures for chat-service persistence tests."""

from __future__ import annotations

import pytest
from sqlalchemy import text
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine
from sqlalchemy.pool import NullPool
from testcontainers.community.postgres import PostgresContainer

from chat_service.infrastructure.db import models  # noqa: F401  (register metadata)
from chat_service.infrastructure.db.models import Base


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
        username="chat",
        password="chat",
        dbname="chat",
        driver="asyncpg",
    ) as postgres:
        yield postgres


@pytest.fixture(scope="session")
def database_url(postgres_container: PostgresContainer) -> str:
    return postgres_container.get_connection_url()


@pytest.fixture
async def session_factory(database_url: str):
    engine = create_async_engine(database_url, poolclass=NullPool)
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
        await connection.execute(
            text(
                "TRUNCATE TABLE messages, dialog_user_state, "
                "dialog_participants, dialogs RESTART IDENTITY CASCADE"
            )
        )
    yield async_sessionmaker(engine, expire_on_commit=False)
    await engine.dispose()
