"""Тесты адаптеров LiveKit без обращения к сети (JWT и проверка вебхуков)."""

from __future__ import annotations

import base64
import hashlib
from datetime import timedelta

import pytest
from livekit import api

from video_service.application.ports import RoomEventKind, WebhookVerificationError
from video_service.domain.value_objects import RoomName
from video_service.infrastructure.livekit.gateway import LiveKitGatewayAdapter
from video_service.infrastructure.livekit.webhook import LiveKitWebhookParser

API_KEY = "devkey"
API_SECRET = "0123456789abcdef0123456789abcdef"


async def test_participant_token_contains_room_grants() -> None:
    gateway = LiveKitGatewayAdapter(
        url="http://localhost:7880",
        api_key=API_KEY,
        api_secret=API_SECRET,
    )

    token = await gateway.create_participant_token(
        room_name=RoomName("call_test"),
        identity="patient-1",
        display_name="Иван",
        ttl=timedelta(minutes=5),
        metadata={"role": "patient"},
    )

    claims = api.TokenVerifier(API_KEY, API_SECRET).verify(token)
    assert claims.identity == "patient-1"
    assert claims.name == "Иван"
    assert claims.video.room == "call_test"
    assert claims.video.room_join is True
    assert claims.video.can_publish is True


def test_webhook_parser_accepts_signed_event() -> None:
    body = '{"event":"room_finished","room":{"name":"call_test"}}'
    digest = base64.b64encode(hashlib.sha256(body.encode()).digest()).decode()
    token = api.AccessToken(API_KEY, API_SECRET).with_sha256(digest).to_jwt()
    parser = LiveKitWebhookParser(api_key=API_KEY, api_secret=API_SECRET)

    event = parser.parse(body.encode(), token)

    assert event is not None
    assert event.kind is RoomEventKind.ROOM_FINISHED
    assert event.room_name.value == "call_test"


def test_webhook_parser_rejects_tampered_body() -> None:
    body = '{"event":"room_finished","room":{"name":"call_test"}}'
    digest = base64.b64encode(hashlib.sha256(body.encode()).digest()).decode()
    token = api.AccessToken(API_KEY, API_SECRET).with_sha256(digest).to_jwt()
    parser = LiveKitWebhookParser(api_key=API_KEY, api_secret=API_SECRET)

    with pytest.raises(WebhookVerificationError):
        parser.parse(body.replace("room_finished", "room_started").encode(), token)
