"""Testcontainers fixtures shared by the file-service integration tests."""

from __future__ import annotations

import pytest
from testcontainers.community.minio import MinioContainer
from testcontainers.community.postgres import PostgresContainer


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
        username="file_service",
        password="file_service",
        dbname="file_service",
        driver="asyncpg",
    ) as postgres:
        yield postgres

@pytest.fixture(scope="session")
def database_url(postgres_container: PostgresContainer) -> str:
    return postgres_container.get_connection_url()

@pytest.fixture(scope="session")
def minio_container() -> MinioContainer:
    
    
    container = MinioContainer(
        image="bitnamilegacy/minio:latest",
        access_key="minioadmin",
        secret_key="minioadmin",
    )
    container.with_env("MINIO_ROOT_USER", "minioadmin")
    container.with_env("MINIO_ROOT_PASSWORD", "minioadmin")
    container.with_kwargs(user="0")
    with container:
        yield container
