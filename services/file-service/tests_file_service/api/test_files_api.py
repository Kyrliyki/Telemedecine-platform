"""Tests for the HTTP contract."""

from __future__ import annotations

from uuid import UUID, uuid4

import httpx

from tests_file_service.api.conftest import ApiClient, FakeContainer, make_settings
from tests_file_service.fakes import build_fake_context

API = "/api/v1"

async def upload(
    client: httpx.AsyncClient,
    chat_id: UUID,
    data: bytes = b"%PDF-1.7 test",
    name: str = "scan.pdf",
    content_type: str = "application/pdf",
    headers: dict[str, str] | None = None,
) -> httpx.Response:
    return await client.post(
        f"{API}/chats/{chat_id}/files",
        files={"file": (name, data, content_type)},
        headers=headers,
    )

async def test_upload_history_and_delete(api: ApiClient) -> None:
    chat_id = uuid4()
    response = await upload(api.client, chat_id)

    assert response.status_code == 201, response.text
    payload = response.json()
    assert payload["status"] == "ready"
    assert payload["kind"] == "document"
    assert payload["file_name"] == "scan.pdf"
    assert payload["size_bytes"] == len(b"%PDF-1.7 test")
    assert payload["download_url"]
    assert payload["checksum_sha256"]
    file_id = payload["id"]

    history = await api.client.get(f"{API}/chats/{chat_id}/files")
    assert history.status_code == 200
    body = history.json()
    assert [item["id"] for item in body["items"]] == [file_id]
    assert body["next_cursor"] is None

    metadata = await api.client.get(f"{API}/files/{file_id}")
    assert metadata.status_code == 200
    assert metadata.json()["download_url"]

    download = await api.client.get(f"{API}/files/{file_id}?download=true")
    assert download.status_code == 200
    assert "download=scan.pdf" in download.json()["download_url"]

    redirect = await api.client.get(f"{API}/files/{file_id}/download", follow_redirects=False)
    assert redirect.status_code == 307
    assert redirect.headers["location"].startswith("https://storage.test/download/")

    deleted = await api.client.delete(f"{API}/files/{file_id}")
    assert deleted.status_code == 204

    assert (await api.client.get(f"{API}/files/{file_id}")).status_code == 404
    assert (await api.client.get(f"{API}/chats/{chat_id}/files")).json()["items"] == []

async def test_pagination_cursor(api: ApiClient) -> None:
    chat_id = uuid4()
    ids = []
    for index in range(5):
        response = await upload(api.client, chat_id, data=f"data-{index}".encode(), name=f"{index}.pdf")
        ids.append(response.json()["id"])
        api.context.db.clock.advance(1)

    first = (await api.client.get(f"{API}/chats/{chat_id}/files?limit=2")).json()
    assert [item["id"] for item in first["items"]] == [ids[4], ids[3]]
    assert first["next_cursor"]

    second = (
        await api.client.get(f"{API}/chats/{chat_id}/files?limit=2&cursor={first['next_cursor']}")
    ).json()
    assert [item["id"] for item in second["items"]] == [ids[2], ids[1]]

    invalid = await api.client.get(f"{API}/chats/{chat_id}/files?limit=2&cursor=not-a-cursor")
    assert invalid.status_code == 422
    assert invalid.json()["error"]["code"] == "validation_error"

async def test_presigned_upload_flow(api: ApiClient) -> None:
    chat_id = uuid4()
    response = await api.client.post(
        f"{API}/chats/{chat_id}/uploads",
        json={
            "file_name": "МРТ колено.pdf",
            "content_type": "application/pdf",
            "size_bytes": 4,
        },
    )
    assert response.status_code == 201, response.text
    ticket = response.json()
    assert ticket["file"]["status"] == "pending"
    assert "upload/" in ticket["upload_url"]
    assert ticket["method"] == "PUT"

    attachment = api.context.db.attachments[UUID(ticket["file"]["id"])]
    api.context.db.storage.put_bytes(attachment.storage_key, b"data")

    completed = await api.client.post(f"{API}/files/{ticket['file']['id']}/complete", json={})
    assert completed.status_code == 200, completed.text
    assert completed.json()["status"] == "ready"
    assert completed.json()["size_bytes"] == 4

    
    second = await api.client.post(
        f"{API}/chats/{chat_id}/uploads",
        json={"file_name": "missing.pdf", "content_type": "application/pdf", "size_bytes": 10},
    )
    conflict = await api.client.post(f"{API}/files/{second.json()['file']['id']}/complete", json={})
    assert conflict.status_code == 409

async def test_upload_validation_errors() -> None:
    context = build_fake_context(max_size_bytes=10, allowed_content_types=frozenset({"image/*"}))
    app = __import__("file_service.presentation.app", fromlist=["create_app"]).create_app(
        settings=make_settings(), container=FakeContainer(context.use_cases)
    )
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
        too_large = await upload(
            client, uuid4(), data=b"12345678901", name="big.png", content_type="image/png"
        )
        assert too_large.status_code == 413
        assert too_large.json()["error"]["code"] == "file_too_large"

        wrong_type = await upload(client, uuid4(), data=b"123", name="a.pdf", content_type="application/pdf")
        assert wrong_type.status_code == 422
        assert wrong_type.json()["error"]["code"] == "validation_error"

        empty = await upload(client, uuid4(), data=b"", name="empty.png", content_type="image/png")
        assert empty.status_code == 422

async def test_chat_access_denied() -> None:
    context = build_fake_context(allowed=False)
    app = __import__("file_service.presentation.app", fromlist=["create_app"]).create_app(
        settings=make_settings(), container=FakeContainer(context.use_cases)
    )
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
        response = await upload(client, uuid4())
        assert response.status_code == 403
        assert response.json()["error"]["code"] == "forbidden"

async def test_only_uploader_can_delete(api: ApiClient) -> None:
    chat_id = uuid4()
    uploader = UUID("10000000-0000-0000-0000-000000000001")
    other = UUID("20000000-0000-0000-0000-000000000002")

    created = await upload(api.client, chat_id, headers={"X-User-Id": str(uploader)})
    assert created.status_code == 201
    file_id = created.json()["id"]

    denied = await api.client.delete(f"{API}/files/{file_id}", headers={"X-User-Id": str(other)})
    assert denied.status_code == 403

    allowed = await api.client.delete(f"{API}/files/{file_id}", headers={"X-User-Id": str(uploader)})
    assert allowed.status_code == 204

async def test_me_and_health(api: ApiClient) -> None:
    me = await api.client.get(f"{API}/me")
    assert me.status_code == 200
    assert me.json()["id"] == str(UUID("00000000-0000-0000-0000-000000000001"))
    assert me.json()["roles"] == ["patient"]

    live = await api.client.get("/health/live")
    assert live.status_code == 200
    assert live.json() == {"status": "ok"}

    ready = await api.client.get("/health/ready")
    assert ready.status_code == 200
    assert ready.json()["status"] == "ok"

async def test_openapi(api: ApiClient) -> None:
    schema = await api.client.get("/openapi.json")
    assert schema.status_code == 200
    paths = schema.json()["paths"]
    assert "/api/v1/chats/{chat_id}/files" in paths
    assert "/api/v1/chats/{chat_id}/uploads" in paths
    assert "/api/v1/files/{file_id}/complete" in paths

async def test_requires_bearer_when_auth_enabled() -> None:
    context = build_fake_context()
    app = __import__("file_service.presentation.app", fromlist=["create_app"]).create_app(
        settings=make_settings(auth_disabled=False), container=FakeContainer(context.use_cases)
    )
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
        response = await client.get(f"{API}/me")
        assert response.status_code == 401
        assert response.headers["www-authenticate"] == "Bearer"
        assert response.json()["error"]["code"] == "unauthenticated"
