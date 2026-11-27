"""In-app notification inbox."""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Annotated
from uuid import UUID

from common_db import session_scope
from fastapi import APIRouter, HTTPException, Query, Response, status

from notification_service.presentation.deps import ContainerDep, CurrentUser
from notification_service.presentation.schemas import (
    NotificationPage,
    NotificationResponse,
    ReadAllResponse,
    UnreadCountResponse,
)

router = APIRouter(prefix="/notifications", tags=["notifications"])


@router.get("", response_model=NotificationPage, summary="Список уведомлений")
async def list_notifications(
    container: ContainerDep,
    user: CurrentUser,
    unread_only: bool = False,
    limit: Annotated[int, Query(ge=1, le=100)] = 50,
    offset: Annotated[int, Query(ge=0)] = 0,
) -> NotificationPage:
    async with session_scope(container.session_factory) as session:
        items, total = await container.repository.list_for_user(
            session,
            user_id=user.subject,
            unread_only=unread_only,
            limit=limit,
            offset=offset,
        )
    return NotificationPage(
        items=[NotificationResponse.from_entity(item) for item in items],
        total=total,
        limit=limit,
        offset=offset,
    )


@router.get(
    "/unread-count",
    response_model=UnreadCountResponse,
    summary="Количество непрочитанных",
)
async def unread_count(container: ContainerDep, user: CurrentUser) -> UnreadCountResponse:
    async with session_scope(container.session_factory) as session:
        count = await container.repository.unread_count(session, user_id=user.subject)
    return UnreadCountResponse(count=count)


@router.get(
    "/{notification_id}",
    response_model=NotificationResponse,
    summary="Одно уведомление",
)
async def get_notification(
    notification_id: UUID,
    container: ContainerDep,
    user: CurrentUser,
) -> NotificationResponse:
    async with session_scope(container.session_factory) as session:
        notification = await container.repository.get_for_user(
            session,
            notification_id=notification_id,
            user_id=user.subject,
        )
    if notification is None:
        raise HTTPException(status_code=404, detail="Notification not found")
    return NotificationResponse.from_entity(notification)


@router.post(
    "/{notification_id}/read",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Отметить уведомление прочитанным",
)
async def mark_read(
    notification_id: UUID,
    container: ContainerDep,
    user: CurrentUser,
) -> Response:
    async with session_scope(container.session_factory) as session:
        updated = await container.repository.mark_read(
            session,
            notification_id=notification_id,
            user_id=user.subject,
            now=datetime.now(UTC),
        )
    if not updated:
        raise HTTPException(status_code=404, detail="Notification not found")
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post(
    "/read-all",
    response_model=ReadAllResponse,
    summary="Отметить все уведомления прочитанными",
)
async def mark_all_read(container: ContainerDep, user: CurrentUser) -> ReadAllResponse:
    async with session_scope(container.session_factory) as session:
        updated = await container.repository.mark_all_read(
            session,
            user_id=user.subject,
            now=datetime.now(UTC),
        )
    return ReadAllResponse(updated=updated)
