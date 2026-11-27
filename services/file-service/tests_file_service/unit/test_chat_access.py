"""ChatService access adapter tests."""

from __future__ import annotations

from uuid import uuid4

import httpx
import pytest

from file_service.application.dto import Actor
from file_service.domain.exceptions import AccessCheckUnavailableError
from file_service.infrastructure.chat_access import (
    AllowAllChatAccessPolicy,
    DenyAllChatAccessPolicy,
    HttpChatAccessPolicy,
)


class FakeJsonCache:
    def __init__(self) -> None:
        self.data: dict[str, dict] = {}

    async def get_json(self, key: str):
        return self.data.get(key)

    async def set_json(self, key: str, value: dict, ttl_seconds: int) -> None:
        self.data[key] = value


def make_policy(handler) -> tuple[HttpChatAccessPolicy, list[str]]:
    policy = HttpChatAccessPolicy(
        base_url="http://chat-service",
        internal_token="secret",
        cache=FakeJsonCache(),  # type: ignore[arg-type]
        cache_ttl_seconds=60,
    )
    calls: list[str] = []

    def wrapped(request: httpx.Request) -> httpx.Response:
        calls.append(f"{request.method} {request.url.path}")
        return handler(request)

    policy._client = httpx.AsyncClient(
        transport=httpx.MockTransport(wrapped),
        base_url="http://chat-service",
        headers={"X-Internal-Token": "secret"},
    )
    return policy, calls


async def test_member_has_access_and_result_is_cached() -> None:
    actor = Actor(id=uuid4())
    chat_id = uuid4()

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"role": "patient"})

    policy, calls = make_policy(handler)
    assert await policy.can_access(actor, chat_id) is True
    assert await policy.can_access(actor, chat_id) is True
    assert len(calls) == 1
    await policy.aclose()


async def test_non_member_has_no_access() -> None:
    actor = Actor(id=uuid4())

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(404)

    policy, _ = make_policy(handler)
    assert await policy.can_access(actor, uuid4()) is False
    await policy.aclose()


async def test_chat_service_error_is_reported_as_unavailable() -> None:
    actor = Actor(id=uuid4())

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(503)

    policy, _ = make_policy(handler)
    with pytest.raises(AccessCheckUnavailableError):
        await policy.can_access(actor, uuid4())
    await policy.aclose()


async def test_moderator_bypasses_chat_service() -> None:
    actor = Actor(id=uuid4(), roles=frozenset({"moderator"}))

    def handler(request: httpx.Request) -> httpx.Response:  # pragma: no cover
        raise AssertionError("must not be called")

    policy, calls = make_policy(handler)
    assert await policy.can_access(actor, uuid4()) is True
    assert calls == []
    await policy.aclose()


async def test_demo_policy_allows_everything() -> None:
    policy = AllowAllChatAccessPolicy()
    assert await policy.can_access(Actor(id=uuid4()), uuid4()) is True
    await policy.aclose()


async def test_deny_policy_blocks_everything() -> None:
    policy = DenyAllChatAccessPolicy()
    assert await policy.can_access(Actor(id=uuid4()), uuid4()) is False
    await policy.aclose()
