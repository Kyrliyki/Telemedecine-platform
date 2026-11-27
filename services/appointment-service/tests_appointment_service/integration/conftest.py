"""Testcontainers fixtures shared by the appointment-service integration tests."""

from __future__ import annotations

import uuid

import psycopg
import pytest
from sqlalchemy import text
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine
from sqlalchemy.pool import NullPool
from testcontainers.community.postgres import PostgresContainer

from appointment.infrastructure.config import get_settings
from appointment.infrastructure.db import models  # noqa: F401  (register metadata)
from appointment.infrastructure.db.base import Base
from appointment.infrastructure.db.migrations import run_migrations


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
        username="appointment",
        password="appointment",
        dbname="appointment",
        driver="asyncpg",
    ) as postgres:
        yield postgres


@pytest.fixture(scope="session")
def database_url(postgres_container: PostgresContainer) -> str:
    return postgres_container.get_connection_url()


@pytest.fixture
async def engine(database_url: str):
    engine = create_async_engine(database_url, poolclass=NullPool)
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
        await connection.execute(
            text("TRUNCATE TABLE appointments, outbox_messages RESTART IDENTITY CASCADE")
        )
    yield engine
    await engine.dispose()


@pytest.fixture
async def session_factory(engine):
    return async_sessionmaker(engine, expire_on_commit=False)


@pytest.fixture
async def migrated_database_url(postgres_container: PostgresContainer, monkeypatch):
    """Create a throw-away database and apply the real Alembic migrations to it."""
    dbname = f"appointment_migration_{uuid.uuid4().hex[:12]}"
    host = postgres_container.get_container_host_ip()
    port = int(postgres_container.get_exposed_port(5432))
    user = postgres_container.username
    password = postgres_container.password
    admin_db = postgres_container.dbname

    def connect(target_db: str):
        return psycopg.connect(
            host=host,
            port=port,
            user=user,
            password=password,
            dbname=target_db,
            autocommit=True,
        )

    with connect(admin_db) as connection:
        connection.execute(f'CREATE DATABASE "{dbname}"')

    migration_url = f"postgresql+asyncpg://{user}:{password}@{host}:{port}/{dbname}"
    monkeypatch.setenv("APP_DB_SCHEMA", "")
    get_settings.cache_clear()
    try:
        await run_migrations(migration_url, "alembic.ini")
        yield migration_url
    finally:
        get_settings.cache_clear()
        with connect(admin_db) as connection:
            connection.execute(f'DROP DATABASE IF EXISTS "{dbname}" WITH (FORCE)')
