"""Вебхуки LiveKit."""

from __future__ import annotations

from fastapi import APIRouter, Request, Response, status

from video_service.application.ports import WebhookVerificationError
from video_service.presentation.dependencies import ContainerDep

router = APIRouter(prefix="/api/v1/webhooks", tags=["webhooks"])


@router.post(
    "/livekit",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Вебхук LiveKit",
    description=(
        "Принимает подписанные события LiveKit (room_started/room_finished) "
        "и синхронизирует статус звонка."
    ),
)
async def livekit_webhook(request: Request, container: ContainerDep) -> Response:
    auth_header = request.headers.get("Authorization")
    if not auth_header:
        raise WebhookVerificationError("Отсутствует заголовок Authorization")

    body = await request.body()
    event = container.webhook_parser.parse(body, auth_header)
    if event is not None:
        await container.handle_livekit_event.execute(event)

    return Response(status_code=status.HTTP_204_NO_CONTENT)
