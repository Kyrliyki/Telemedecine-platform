"""WebSocket protocol tests for chat-service."""

from __future__ import annotations

from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from tests_chat_service.conftest import CLIENT_ID

pytestmark = pytest.mark.integration


def test_ping_pong(app) -> None:
    with TestClient(app) as client, client.websocket_connect("/ws") as ws:
        ws.send_json({"type": "ping"})
        assert ws.receive_json() == {"type": "pong"}


def test_invalid_frame_and_unknown_type(app) -> None:
    with TestClient(app) as client, client.websocket_connect("/ws") as ws:
        ws.send_text("not-json")
        assert ws.receive_json()["payload"]["code"] == "VALIDATION_ERROR"

        ws.send_json({"type": "unknown"})
        assert ws.receive_json()["payload"]["code"] == "VALIDATION_ERROR"


def test_message_send_returns_ack_and_persists(app, context) -> None:
    dialog_id = next(iter(context.dialogs.dialogs))
    client_msg_id = uuid4()

    with TestClient(app) as client, client.websocket_connect("/ws") as ws:
        ws.send_json(
            {
                "type": "message.send",
                "payload": {
                    "dialog_id": str(dialog_id),
                    "client_msg_id": str(client_msg_id),
                    "content": "hello over ws",
                },
            }
        )
        ack = ws.receive_json()

    assert ack["type"] == "message.ack"
    assert ack["payload"]["client_msg_id"] == str(client_msg_id)
    assert len(context.messages.messages) == 1
    stored = next(iter(context.messages.messages.values()))
    assert stored.content == "hello over ws"
    assert stored.author_id == CLIENT_ID


async def _save_message(context, dialog_id):
    message, _ = await context.messages.save(
        message_id=uuid4(),
        dialog_id=dialog_id,
        author_id=CLIENT_ID,
        content="read me",
        client_msg_id=uuid4(),
    )
    return message


def test_read_and_typing(app, context) -> None:
    import asyncio

    dialog_id = next(iter(context.dialogs.dialogs))
    message = asyncio.run(_save_message(context, dialog_id))

    with TestClient(app) as client, client.websocket_connect("/ws") as ws:
        ws.send_json(
            {
                "type": "read",
                "payload": {
                    "dialog_id": str(dialog_id),
                    "up_to_message_id": str(message.id),
                },
            }
        )
        ws.send_json({"type": "typing", "payload": {"dialog_id": str(dialog_id)}})
        ws.send_json({"type": "ping"})
        assert ws.receive_json() == {"type": "pong"}

    assert context.states.reads == [(dialog_id, CLIENT_ID, message.id)]
    assert context.publisher.bulk_events
