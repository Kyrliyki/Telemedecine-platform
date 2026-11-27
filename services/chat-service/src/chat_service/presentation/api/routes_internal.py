"""Internal ChatService API used by FileService/VideoService and Java services."""

from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request

from chat_service.infrastructure.db.repositories import SqlDialogRepository
from chat_service.presentation.api.internal_auth import internal_token_guard

router = APIRouter(
    prefix="/internal",
    tags=["internal"],
    dependencies=[Depends(internal_token_guard())],
)


async def _membership(request: Request, room_id: UUID, user_id: UUID) -> dict[str, bool]:
    session_factory = request.app.state.session_factory
    async with session_factory() as session:
        repository = SqlDialogRepository(session)
        dialog = await repository.get(room_id)
        if dialog is None:
            raise HTTPException(404, "room not found")
        if not await repository.is_participant(room_id, user_id):
            raise HTTPException(403, "user is not a room member")
    return {"member": True}


@router.get("/chat/rooms/{room_id}/members/{user_id}")
async def room_membership(room_id: UUID, user_id: UUID, request: Request) -> dict[str, bool]:
    return await _membership(request, room_id, user_id)


@router.get("/v1/chats/{room_id}/members/{user_id}")
async def legacy_room_membership(
    room_id: UUID, user_id: UUID, request: Request
) -> dict[str, bool]:
    """Backwards-compatible path used by the first FileService client."""
    return await _membership(request, room_id, user_id)
