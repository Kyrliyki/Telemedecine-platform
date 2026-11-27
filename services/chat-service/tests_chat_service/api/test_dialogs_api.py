"""HTTP API tests for chat-service (ASGI in-process, fake repositories)."""

from __future__ import annotations

from uuid import uuid4

from fastapi import FastAPI

from chat_service.application.ports.request_gateway import RequestInfo
from chat_service.domain.value_objects import RequestStatus
from chat_service.presentation.api.deps import current_user
from tests_chat_service.conftest import CLIENT_ID, OPERATOR_ID, STRANGER_ID


def _participants() -> list[dict[str, str]]:
    return [
        {"user_id": str(CLIENT_ID), "role": "client"},
        {"user_id": str(OPERATOR_ID), "role": "operator"},
    ]


async def test_create_dialog_is_idempotent(client, context) -> None:
    body = {"request_id": "req-1", "participants": _participants()}

    first = await client.post("/api/v1/dialogs", json=body)
    second = await client.post("/api/v1/dialogs", json=body)

    assert first.status_code == 200
    assert second.status_code == 200
    assert first.json()["id"] == second.json()["id"]
    assert len(context.dialogs.dialogs) == 1
    assert {p["user_id"] for p in first.json()["participants"]} == {
        str(CLIENT_ID),
        str(OPERATOR_ID),
    }


async def test_create_dialog_rejects_foreign_actor(client, app: FastAPI, context) -> None:
    app.dependency_overrides[current_user] = lambda: STRANGER_ID
    context.gateway.info = RequestInfo(
        request_id="req-2",
        status=RequestStatus.OPEN,
        patient_id=CLIENT_ID,
        doctor_id=OPERATOR_ID,
    )

    response = await client.post(
        "/api/v1/dialogs",
        json={"request_id": "req-2", "participants": _participants()},
    )

    assert response.status_code == 403


async def test_get_dialog_by_request_returns_history(client, context) -> None:
    dialog = next(iter(context.dialogs.dialogs.values()))
    message, _ = await context.messages.save(
        message_id=uuid4(),
        dialog_id=dialog.id,
        author_id=CLIENT_ID,
        content="hello",
        client_msg_id=uuid4(),
    )

    response = await client.get("/api/v1/dialogs/by-request/req-1")

    assert response.status_code == 200
    payload = response.json()
    assert payload["id"] == str(dialog.id)
    assert payload["messages"]["items"][0]["id"] == str(message.id)


async def test_list_dialogs_for_participant(client, context) -> None:
    response = await client.get("/api/v1/dialogs")

    assert response.status_code == 200
    assert len(response.json()["items"]) == 1
    assert response.json()["items"][0]["request_id"] == "req-1"


async def test_update_dialog_status_and_archive(client, context) -> None:
    dialog_id = next(iter(context.dialogs.dialogs))

    response = await client.patch(
        f"/api/v1/dialogs/{dialog_id}",
        json={"request_status": "closed", "archived": True},
    )

    assert response.status_code == 200
    assert response.json()["request_status"] == "closed"
    assert (dialog_id, CLIENT_ID, True) in context.states.archived


async def test_mark_read_and_get_history(client, context) -> None:
    dialog_id = next(iter(context.dialogs.dialogs))
    message, _ = await context.messages.save(
        message_id=uuid4(),
        dialog_id=dialog_id,
        author_id=OPERATOR_ID,
        content="read me",
        client_msg_id=uuid4(),
    )

    history = await client.get(f"/api/v1/dialogs/{dialog_id}/messages")
    read = await client.post(
        f"/api/v1/dialogs/{dialog_id}/read",
        json={"up_to_message_id": str(message.id)},
    )

    assert history.status_code == 200
    assert history.json()["items"][0]["id"] == str(message.id)
    assert read.status_code == 204
    assert context.states.reads == [(dialog_id, CLIENT_ID, message.id)]


async def test_delete_dialog(client, context) -> None:
    dialog_id = next(iter(context.dialogs.dialogs))

    response = await client.delete(f"/api/v1/dialogs/{dialog_id}")
    second = await client.delete(f"/api/v1/dialogs/{dialog_id}")

    assert response.status_code == 204
    assert second.status_code == 404
    assert dialog_id not in context.dialogs.dialogs


async def test_non_participant_gets_403(client, app: FastAPI) -> None:
    app.dependency_overrides[current_user] = lambda: STRANGER_ID

    response = await client.get("/api/v1/dialogs/by-request/req-1")

    assert response.status_code == 403
