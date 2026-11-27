"""Testcontainers fixture for the video-service LiveKit integration test."""

from __future__ import annotations

import pytest
from testcontainers.core.container import DockerContainer
from testcontainers.core.wait_strategies import LogMessageWaitStrategy


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
def livekit_url() -> str:
    container = (
        DockerContainer("livekit/livekit-server:v1.13.11")
        .with_command(["--dev", "--bind", "0.0.0.0"])
        .with_exposed_ports(7880)
        .waiting_for(LogMessageWaitStrategy("starting LiveKit server"))
    )
    with container:
        host = container.get_container_host_ip()
        port = container.get_exposed_port(7880)
        yield f"http://{host}:{port}"
