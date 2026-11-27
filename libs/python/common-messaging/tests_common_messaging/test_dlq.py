from __future__ import annotations

import asyncio
from types import SimpleNamespace
from typing import Any

import common_messaging.dlq_admin as dlq_admin
from common_messaging import (
    DlqAdminService,
    DlqNotFoundError,
    DlqQueueCatalog,
    DlqQueueDefinition,
    DlqQueueSpec,
    RabbitMQSettings,
    build_dlq_router,
)
from fastapi import FastAPI
from fastapi.testclient import TestClient


class FakeMessage:
    def __init__(self, headers: dict[str, Any], body: bytes = b"{}") -> None:
        self.body = body
        self.headers = headers
        self.content_type = "application/json"
        self.content_encoding = None
        self.message_id = "message-1"
        self.correlation_id = "correlation-1"
        self.acked = False
        self.nacked = False
        self.requeued = False

    async def ack(self) -> None:
        self.acked = True

    async def nack(self, requeue: bool = False) -> None:
        self.nacked = True
        self.requeued = requeue


class FakeQueue:
    def __init__(self, messages: list[FakeMessage]) -> None:
        self.messages = messages
        self.declaration_result = SimpleNamespace(message_count=len(messages))

    async def get(self, **_kwargs: Any) -> FakeMessage | None:
        return self.messages.pop(0) if self.messages else None

    async def purge(self) -> SimpleNamespace:
        count = len(self.messages)
        self.messages.clear()
        return SimpleNamespace(message_count=count)


class FakeExchange:
    def __init__(self) -> None:
        self.published: list[tuple[str, FakeMessage]] = []

    async def publish(self, message: FakeMessage, routing_key: str, **_kwargs: Any) -> None:
        self.published.append((routing_key, message))


class FakeChannel:
    def __init__(self, queues: dict[str, FakeQueue], exchange: FakeExchange) -> None:
        self.queues = queues
        self.exchange = exchange
        self.default_exchange = exchange
        self.closed = False

    async def declare_queue(self, name: str, **_kwargs: Any) -> FakeQueue:
        if name not in self.queues:
            raise RuntimeError("queue not found")
        return self.queues[name]

    async def declare_exchange(self, _name: str, *_args: Any, **_kwargs: Any) -> FakeExchange:
        return self.exchange

    async def get_exchange(self, _name: str, **_kwargs: Any) -> FakeExchange:
        return self.exchange

    async def close(self) -> None:
        self.closed = True


class FakeConnection:
    is_closed = False

    def __init__(self, channel: FakeChannel) -> None:
        self._channel = channel
        self.closed = False

    async def channel(self, **_kwargs: Any) -> FakeChannel:
        return self._channel

    async def close(self) -> None:
        self.closed = True


def build_service(
    messages: list[FakeMessage] | None = None,
) -> tuple[DlqAdminService, FakeChannel, FakeExchange]:
    catalog = DlqQueueCatalog(
        [
            DlqQueueDefinition(
                key="auth",
                queue="user-service.auth.events.dlq",
                exchange="auth.events",
                description="auth events",
            )
        ]
    )
    service = DlqAdminService(RabbitMQSettings(url="amqp://unused"), catalog)
    exchange = FakeExchange()
    channel = FakeChannel(
        {"user-service.auth.events.dlq": FakeQueue(messages or [])},
        exchange,
    )
    service._connection = FakeConnection(channel)  # type: ignore[assignment]
    return service, channel, exchange


def test_catalog_is_case_insensitive() -> None:
    catalog = DlqQueueCatalog([DlqQueueDefinition("auth", "q", "ex")])
    assert catalog.find("AUTH") is not None
    assert catalog.find("missing") is None


def test_replay_publishes_and_acks() -> None:
    message = FakeMessage(
        {
            "eventType": "auth.user.registered.v1",
            "x-death": [{"routing-keys": ["old.key"]}],
        }
    )
    service, _, exchange = build_service([message])

    result = asyncio.run(service.replay("auth", limit=10))

    assert result.replayed == 1
    assert result.failed == 0
    assert exchange.published[0][0] == "auth.user.registered.v1"
    assert "x-death" not in exchange.published[0][1].headers
    assert message.acked is True


def test_replay_requeues_message_without_routing_key() -> None:
    message = FakeMessage({})
    service, _, _ = build_service([message])

    result = asyncio.run(service.replay("auth", limit=10))

    assert result.failed == 1
    assert message.nacked is True
    assert message.requeued is True


def test_dry_run_does_not_consume() -> None:
    service, _, exchange = build_service([FakeMessage({"eventType": "auth.x"})])

    result = asyncio.run(service.replay("auth", limit=10, dry_run=True))

    assert result.dry_run is True
    assert result.candidates == 1
    assert result.replayed == 0
    assert exchange.published == []


def test_purge_returns_count() -> None:
    service, _, _ = build_service([FakeMessage({"eventType": "auth.x"})])

    result = asyncio.run(service.purge("auth"))

    assert result.purged == 1


def test_unknown_queue_key_is_rejected() -> None:
    service, _, _ = build_service()

    try:
        asyncio.run(service.replay("unknown", limit=10))
    except DlqNotFoundError as exception:
        assert "Unknown DLQ" in str(exception)
    else:
        raise AssertionError("Expected DlqNotFoundError")


def test_internal_router_requires_token_and_returns_camel_case(monkeypatch) -> None:
    _, channel, _ = build_service([FakeMessage({"eventType": "auth.x"})])

    async def fake_connect(*_args: Any, **_kwargs: Any) -> FakeConnection:
        return FakeConnection(channel)

    monkeypatch.setattr(dlq_admin.aio_pika, "connect_robust", fake_connect)

    app = FastAPI()
    app.include_router(
        build_dlq_router(
            rabbitmq_url="amqp://unused",
            internal_token="internal-token",
            specs=[
                DlqQueueSpec(
                    key="auth",
                    queue="user-service.auth.events.dlq",
                    exchange="auth.events",
                    description="auth events",
                )
            ],
        )
    )
    client = TestClient(app)

    assert client.get("/internal/dlq").status_code == 401
    response = client.get(
        "/internal/dlq",
        headers={"X-Internal-Token": "internal-token"},
    )
    assert response.status_code == 200
    assert response.json()[0]["queue"] == "user-service.auth.events.dlq"

    replay = client.post(
        "/internal/dlq/auth/replay?limit=10&dryRun=true",
        headers={"X-Internal-Token": "internal-token"},
    )
    assert replay.status_code == 200
    assert replay.json()["dryRun"] is True
