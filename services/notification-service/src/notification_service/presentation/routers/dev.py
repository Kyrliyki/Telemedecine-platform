"""Development helpers: create notifications and contact projections."""

from __future__ import annotations

from datetime import UTC, datetime
from uuid import uuid4

from common_db import session_scope
from fastapi import APIRouter, Depends, HTTPException

from notification_service.domain.entities import NotificationDraft
from notification_service.domain.enums import NotificationChannel
from notification_service.presentation.deps import ContainerDep, CurrentUser, require_admin
from notification_service.presentation.schemas import (
    DevContactRequest,
    DevDispatchResponse,
    DevNotificationRequest,
)

router = APIRouter(
    prefix="/dev",
    tags=["development"],
    dependencies=[Depends(require_admin)],
)


@router.post("/notifications", summary="Создать тестовое уведомление")
async def create_test_notification(
    body: DevNotificationRequest,
    container: ContainerDep,
    user: CurrentUser,
) -> dict[str, str]:
    user_id = body.user_id or user.subject
    channel = NotificationChannel(body.channel)

    recipient_email = body.email
    if channel is NotificationChannel.EMAIL and not recipient_email:
        async with session_scope(container.session_factory) as session:
            contacts = await container.repository.get_contacts(session, user_ids={user_id})
        recipient_email = contacts.get(user_id)

    if channel is NotificationChannel.EMAIL and not recipient_email:
        raise HTTPException(
            status_code=400,
            detail="Для email-уведомления нужен email или запись в notification_contact",
        )

    draft = NotificationDraft(
        user_id=user_id,
        kind=body.kind,
        channel=channel,
        title=body.title,
        body=body.body,
        payload={"source": "dev"},
        recipient_email=recipient_email,
        dedup_key=f"dev:{uuid4()}",
    )
    async with session_scope(container.session_factory) as session:
        created = await container.repository.add_many(session, [draft])
    if not created:
        raise HTTPException(status_code=409, detail="Notification already exists")
    return {"status": "queued"}


@router.post("/contacts", summary="Задать email пользователя для доставки")
async def upsert_contact(body: DevContactRequest, container: ContainerDep) -> dict[str, str]:
    async with session_scope(container.session_factory) as session:
        await container.repository.upsert_contact(
            session,
            user_id=body.user_id,
            email=body.email,
            now=datetime.now(UTC),
        )
    return {"status": "ok"}


@router.post(
    "/dispatch",
    response_model=DevDispatchResponse,
    summary="Один проход доставки (без ожидания фонового таймера)",
)
async def dispatch_once(container: ContainerDep) -> DevDispatchResponse:
    processed = await container.dispatcher.run_once()
    return DevDispatchResponse(processed=processed)
