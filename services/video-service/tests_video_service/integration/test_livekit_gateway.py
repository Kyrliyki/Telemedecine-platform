"""Integration test against a real LiveKit server started by Testcontainers."""

from __future__ import annotations

from datetime import timedelta

import pytest

from video_service.domain.value_objects import RoomName
from video_service.infrastructure.livekit.gateway import LiveKitGatewayAdapter

pytestmark = pytest.mark.integration

async def test_create_and_delete_room(livekit_url: str) -> None:
    
    gateway = LiveKitGatewayAdapter(url=livekit_url, api_key="devkey", api_secret="secret")
    room_name = RoomName("integration_test_room")

    try:
        assert await gateway.healthcheck() is True

        await gateway.create_room(
            room_name=room_name,
            metadata={"source": "integration-test"},
            max_participants=2,
        )
        token = await gateway.create_participant_token(
            room_name=room_name,
            identity="test-user",
            display_name="Test User",
            ttl=timedelta(minutes=5),
            metadata={"role": "patient"},
        )
        assert token.count(".") == 2
    finally:
        await gateway.delete_room(room_name=room_name)
        await gateway.aclose()
