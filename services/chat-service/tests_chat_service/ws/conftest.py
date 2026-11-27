"""WebSocket test app: real manager/Redis, fake repositories and use-case ports."""

from __future__ import annotations

import pytest
from fastapi import FastAPI
from testcontainers.core.container import DockerContainer
from testcontainers.core.wait_strategies import LogMessageWaitStrategy

from chat_service.application.use_cases.mark_read import MarkRead
from chat_service.application.use_cases.send_message import SendMessage
from chat_service.application.use_cases.send_typing import SendTyping
from chat_service.config import get_settings
from chat_service.presentation.ws.endpoint import router as ws_router
from chat_service.presentation.ws.manager import ConnectionManager
from tests_chat_service.conftest import CLIENT_ID


def _docker_available() -> bool:
    try:
        from testcontainers.core.docker_client import DockerClient

        DockerClient().client.ping()
    except Exception:
        return False
    return True


DOCKER_AVAILABLE = _docker_available()


class FakePresence:
    def __init__(self) -> None:
        self.online: set = set()

    async def set_online(self, user_id) -> None:
        self.online.add(user_id)

    async def set_offline(self, user_id) -> None:
        self.online.discard(user_id)


@pytest.fixture(scope="session", autouse=True)
def require_docker() -> None:
    if not DOCKER_AVAILABLE:
        pytest.skip("Docker is required for WebSocket integration tests")


@pytest.fixture(scope="session")
def redis_container():
    container = (
        DockerContainer("redis:7-alpine")
        .with_exposed_ports(6379)
        .waiting_for(LogMessageWaitStrategy("Ready to accept connections"))
    )
    with container:
        yield container


@pytest.fixture(scope="session")
def redis_url(redis_container) -> str:
    host = redis_container.get_container_host_ip()
    port = redis_container.get_exposed_port(6379)
    return f"redis://{host}:{port}/0"


@pytest.fixture
def app(context, redis_url: str, monkeypatch):
    monkeypatch.setenv("AUTH_DISABLED", "true")
    monkeypatch.setenv("TEST_USER_ID", str(CLIENT_ID))
    get_settings.cache_clear()

    import redis.asyncio as aioredis

    redis = aioredis.from_url(redis_url, decode_responses=True)
    app = FastAPI()
    app.state.ws_manager = ConnectionManager(redis)
    app.state.presence = FakePresence()
    app.state.send_message = SendMessage(
        context.dialogs,
        context.messages,
        context.states,
        context.publisher,
        context.audit,
        context.rate_limiter,
    )
    app.state.send_typing = SendTyping(context.dialogs, context.publisher)
    app.state.mark_read = MarkRead(context.dialogs, context.states, context.publisher)
    app.state.publisher = context.publisher
    app.include_router(ws_router)
    yield app
    get_settings.cache_clear()
