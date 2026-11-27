"""API-тесты (ASGI, без реальных LiveKit/Postgres)."""

from __future__ import annotations

import uuid

from httpx import AsyncClient

from tests_video_service.conftest import VideoTestContext, make_token
from video_service.application.ports import LiveKitRoomEvent, RoomEventKind
from video_service.domain.value_objects import RoomName

APPOINTMENT_ID = str(uuid.UUID(int=100))


def auth(identity: str = "doctor-1", roles: list[str] | None = None) -> dict[str, str]:
    token = make_token(identity, roles or ["doctor"])
    return {"Authorization": f"Bearer {token}"}


async def test_requires_authentication(client: AsyncClient) -> None:
    response = await client.post(
        "/api/v1/calls",
        json={"appointment_id": APPOINTMENT_ID, "patient_id": "patient-1"},
    )

    assert response.status_code == 401
    assert response.json()["error"]["code"] == "http_error"


async def test_create_call_returns_201(client: AsyncClient, context: VideoTestContext) -> None:
    response = await client.post(
        "/api/v1/calls",
        headers=auth(),
        json={"appointment_id": APPOINTMENT_ID, "patient_id": "patient-1"},
    )

    assert response.status_code == 201, response.text
    body = response.json()
    assert body["status"] == "created"
    assert body["doctor_id"] == "doctor-1"
    assert body["room_name"].startswith("call_")
    assert response.headers["X-Request-ID"]
    assert len(context.livekit.created_rooms) == 1


async def test_create_call_is_idempotent(client: AsyncClient) -> None:
    payload = {"appointment_id": APPOINTMENT_ID, "patient_id": "patient-1"}

    first = await client.post("/api/v1/calls", headers=auth(), json=payload)
    second = await client.post("/api/v1/calls", headers=auth(), json=payload)

    assert first.status_code == 201
    assert second.status_code == 200
    assert second.headers["Idempotent-Replayed"] == "true"
    assert first.json()["id"] == second.json()["id"]


async def test_livekit_failure_returns_503(
    client: AsyncClient,
    context: VideoTestContext,
) -> None:
    context.livekit.fail_create = True

    response = await client.post(
        "/api/v1/calls",
        headers=auth(),
        json={"appointment_id": APPOINTMENT_ID, "patient_id": "patient-1"},
    )

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "livekit_unavailable"
    assert context.store.calls == {}


async def test_patient_cannot_create_call(client: AsyncClient) -> None:
    response = await client.post(
        "/api/v1/calls",
        headers=auth("patient-1", ["patient"]),
        json={"appointment_id": APPOINTMENT_ID, "patient_id": "patient-1"},
    )

    assert response.status_code == 403
    assert response.json()["error"]["code"] == "forbidden"


async def test_join_returns_livekit_token(client: AsyncClient) -> None:
    created = await client.post(
        "/api/v1/calls",
        headers=auth(),
        json={"appointment_id": APPOINTMENT_ID, "patient_id": "patient-1"},
    )
    call_id = created.json()["id"]

    response = await client.post(
        f"/api/v1/calls/{call_id}/token",
        headers=auth("patient-1", ["patient"]),
        json={"display_name": "Иван"},
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["token"].endswith(".patient-1")
    assert body["livekit_url"] == "ws://livekit.test:7880"
    assert body["call"]["status"] == "active"


async def test_stranger_cannot_read_call(client: AsyncClient) -> None:
    created = await client.post(
        "/api/v1/calls",
        headers=auth(),
        json={"appointment_id": APPOINTMENT_ID, "patient_id": "patient-1"},
    )
    call_id = created.json()["id"]

    response = await client.get(
        f"/api/v1/calls/{call_id}",
        headers=auth("stranger", ["patient"]),
    )

    assert response.status_code == 403


async def test_list_shows_only_own_calls(client: AsyncClient) -> None:
    await client.post(
        "/api/v1/calls",
        headers=auth(),
        json={"appointment_id": APPOINTMENT_ID, "patient_id": "patient-1"},
    )

    own = await client.get("/api/v1/calls", headers=auth())
    foreign = await client.get("/api/v1/calls", headers=auth("doctor-2"))

    assert own.status_code == 200
    assert len(own.json()["items"]) == 1
    assert foreign.json()["items"] == []


async def test_end_call(client: AsyncClient) -> None:
    created = await client.post(
        "/api/v1/calls",
        headers=auth(),
        json={"appointment_id": APPOINTMENT_ID, "patient_id": "patient-1"},
    )
    call_id = created.json()["id"]

    ended = await client.post(f"/api/v1/calls/{call_id}/end", headers=auth())

    assert ended.status_code == 200
    assert ended.json()["status"] == "ended"
    assert ended.json()["ended_at"] is not None


async def test_validation_error_format(client: AsyncClient) -> None:
    response = await client.post(
        "/api/v1/calls",
        headers=auth(),
        json={"appointment_id": APPOINTMENT_ID},
    )

    assert response.status_code == 422
    error = response.json()["error"]
    assert error["code"] == "validation_error"
    assert error["details"]


async def test_invalid_call_id_returns_422(client: AsyncClient) -> None:
    response = await client.get("/api/v1/calls/not-a-uuid", headers=auth())

    assert response.status_code == 422


async def test_health_live(client: AsyncClient) -> None:
    response = await client.get("/health/live")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "checks": None}


async def test_readiness_reports_livekit(client: AsyncClient) -> None:
    response = await client.get("/health/ready")

    assert response.status_code == 200
    assert response.json()["checks"] == {"livekit": True}


async def test_webhook_requires_signature(client: AsyncClient) -> None:
    response = await client.post("/api/v1/webhooks/livekit", content=b"{}")

    assert response.status_code == 401
    assert response.json()["error"]["code"] == "invalid_webhook_signature"


async def test_webhook_finishes_call(
    client: AsyncClient,
    context: VideoTestContext,
) -> None:
    created = await client.post(
        "/api/v1/calls",
        headers=auth(),
        json={"appointment_id": APPOINTMENT_ID, "patient_id": "patient-1"},
    )
    room_name = created.json()["room_name"]
    context.webhook_parser.next_event = LiveKitRoomEvent(
        kind=RoomEventKind.ROOM_FINISHED,
        room_name=RoomName(room_name),
        event_id="evt-1",
    )

    response = await client.post(
        "/api/v1/webhooks/livekit",
        headers={"Authorization": "signed-webhook-token"},
        content=b"{}",
    )

    assert response.status_code == 204
    stored = await client.get(f"/api/v1/calls/{created.json()['id']}", headers=auth())
    assert stored.json()["status"] == "ended"

async def test_video_alias_exposes_same_api(client: AsyncClient) -> None:
    payload = {
        "appointment_id": str(uuid.UUID(int=101)),
        "patient_id": "patient-1",
    }

    created = await client.post("/api/v1/video", headers=auth(), json=payload)
    assert created.status_code == 201, created.text
    call_id = created.json()["id"]

    fetched = await client.get(f"/api/v1/video/{call_id}", headers=auth())
    assert fetched.status_code == 200
    assert fetched.json()["id"] == call_id
