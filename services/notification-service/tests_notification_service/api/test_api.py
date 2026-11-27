"""Inbox API tests."""

from __future__ import annotations

from uuid import uuid4

from notification_service.domain.entities import NotificationDraft
from notification_service.domain.enums import NotificationChannel
from tests_notification_service.conftest import InMemoryNotificationRepository, make_token


def auth(settings) -> dict[str, str]:
    return {"Authorization": f"Bearer {make_token(settings)}"}


def admin_auth(settings) -> dict[str, str]:
    return {"Authorization": f"Bearer {make_token(settings, roles=['ADMIN'])}"}


async def seed(repository: InMemoryNotificationRepository, count: int = 2) -> None:
    drafts = [
        NotificationDraft(
            user_id="patient-1",
            kind="system.test",
            channel=NotificationChannel.IN_APP,
            title=f"Уведомление {index}",
            body="Тело",
            payload={},
            dedup_key=f"test:{uuid4()}",
        )
        for index in range(count)
    ]
    await repository.add_many(None, drafts)


async def test_requires_bearer_token(client) -> None:
    response = await client.get("/api/v1/notifications")
    assert response.status_code == 401


async def test_inbox_read_flow(client, settings, repository) -> None:
    await seed(repository)

    listing = await client.get("/api/v1/notifications", headers=auth(settings))
    assert listing.status_code == 200
    body = listing.json()
    assert body["total"] == 2
    assert len(body["items"]) == 2

    unread = await client.get("/api/v1/notifications/unread-count", headers=auth(settings))
    assert unread.json()["count"] == 2

    first_id = body["items"][0]["id"]
    read = await client.post(
        f"/api/v1/notifications/{first_id}/read",
        headers=auth(settings),
    )
    assert read.status_code == 204

    unread = await client.get("/api/v1/notifications/unread-count", headers=auth(settings))
    assert unread.json()["count"] == 1

    read_all = await client.post("/api/v1/notifications/read-all", headers=auth(settings))
    assert read_all.json()["updated"] == 1

    unread = await client.get("/api/v1/notifications/unread-count", headers=auth(settings))
    assert unread.json()["count"] == 0


async def test_cannot_read_another_users_notification(client, settings, repository) -> None:
    await repository.add_many(
        None,
        [
            NotificationDraft(
                user_id="doctor-1",
                kind="system.test",
                channel=NotificationChannel.IN_APP,
                title="Чужое",
                body="Тело",
                payload={},
                dedup_key=f"test:{uuid4()}",
            )
        ],
    )
    notification_id = next(iter(repository.items))

    response = await client.post(
        f"/api/v1/notifications/{notification_id}/read",
        headers=auth(settings),
    )
    assert response.status_code == 404


async def test_dev_endpoints_create_notification_and_contact(
    client,
    settings,
    repository,
) -> None:
    contact = await client.post(
        "/api/v1/dev/contacts",
        headers=admin_auth(settings),
        json={"user_id": "patient-1", "email": "patient@example.test"},
    )
    assert contact.status_code == 200
    assert repository.contacts["patient-1"] == "patient@example.test"

    created = await client.post(
        "/api/v1/dev/notifications",
        headers=admin_auth(settings),
        json={
            "title": "Тест",
            "body": "Проверка inbox",
            "channel": "in_app",
        },
    )
    assert created.status_code == 200
    assert created.json()["status"] == "queued"
    assert len(repository.items) == 1


async def test_dev_endpoints_are_admin_only(client, settings) -> None:
    response = await client.post(
        "/api/v1/dev/contacts",
        headers=auth(settings),
        json={"user_id": "patient-1", "email": "patient@example.test"},
    )
    assert response.status_code == 403
