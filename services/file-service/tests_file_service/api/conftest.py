"""API tests use the real ASGI app with in-memory adapters."""

from __future__ import annotations

from dataclasses import dataclass, field
from uuid import UUID

import httpx
import pytest

from file_service.application.use_cases import UseCases
from file_service.infrastructure.config import Settings
from file_service.presentation.app import create_app
from tests_file_service.fakes import FakeContext

DEV_USER_ID = UUID("00000000-0000-0000-0000-000000000001")


class FakeContainer:
    def __init__(self, use_cases: UseCases) -> None:
        self.use_cases = use_cases
        self.started = False

    async def start(self) -> None:
        self.started = True

    async def stop(self) -> None:
        self.started = False

    async def health(self) -> dict:
        return {"status": "ok", "components": {"database": "ok", "storage": "ok"}}


@dataclass
class ApiClient:
    client: httpx.AsyncClient
    context: FakeContext
    app: object = field(default=None)


def make_settings(**overrides) -> Settings:
    values = {
        "auth_disabled": True,
        "dev_user_id": str(DEV_USER_ID),
        "log_json": False,
        "cors_origins": "*",
        "api_prefix": "/api/v1",
        "s3_presign_ttl_seconds": 900,
    }
    values.update(overrides)
    return Settings(**values)


@pytest.fixture
async def api(fake_context: FakeContext):
    app = create_app(settings=make_settings(), container=FakeContainer(fake_context.use_cases))
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
        yield ApiClient(client=client, context=fake_context, app=app)
