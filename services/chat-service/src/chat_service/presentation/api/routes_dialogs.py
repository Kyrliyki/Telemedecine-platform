from typing import Literal
from uuid import UUID

from fastapi import APIRouter, HTTPException, Query, Request, Response

from chat_service.application.use_cases.create_or_get_dialog import CreateOrGetDialog
from chat_service.application.use_cases.delete_dialog import DeleteDialog
from chat_service.application.use_cases.get_dialog_by_request import GetDialogByRequest
from chat_service.application.use_cases.list_dialogs import ListDialogs
from chat_service.application.use_cases.update_dialog import UpdateDialog
from chat_service.domain.entities import Participant
from chat_service.domain.exceptions import DialogNotFoundError, NotAParticipantError
from chat_service.domain.value_objects import ParticipantRole, RequestStatus
from chat_service.presentation.api.deps import CurrentUser
from chat_service.presentation.api.schemas import (
    CreateDialogIn,
    DialogOut,
    DialogWithHistoryOut,
    ParticipantOut,
    UpdateDialogIn,
)

router = APIRouter(prefix="/api/v1/dialogs", tags=["dialogs"])


def _dialog_out(d) -> DialogOut:
    return DialogOut(
        id=d.id, request_id=d.request_id, request_status=d.request_status.value,
        participants=[ParticipantOut(user_id=p.user_id, role=p.role.value) for p in d.participants],
        created_at=d.created_at, updated_at=d.updated_at,
    )


@router.post("", response_model=DialogOut)
async def create_dialog(
    body: CreateDialogIn,
    request: Request,
    user: CurrentUser,
):
    uc: CreateOrGetDialog = request.app.state.create_or_get_dialog
    participants = [Participant(user_id=p.user_id, role=ParticipantRole(p.role))
                    for p in body.participants]
    try:
        dialog, created = await uc.execute(
            actor_id=user,
            request_id=body.request_id,
            participants=participants,
            request_status=RequestStatus(body.request_status),
        )
    except NotAParticipantError:
        raise HTTPException(403, "not a participant") from None
    request.app.state.response_status = 201 if created else 200
    return _dialog_out(dialog)


@router.get("/by-request/{request_id}", response_model=DialogWithHistoryOut)
async def get_dialog_by_request(
    request_id: str,
    request: Request,
    user: CurrentUser,
    limit: int = Query(50, le=200),
):
    uc: GetDialogByRequest = request.app.state.get_dialog_by_request
    try:
        dialog, items, next_cursor = await uc.execute(
            request_id=request_id, user_id=user, limit=limit
        )
    except DialogNotFoundError:
        raise HTTPException(404, "dialog not found") from None
    except NotAParticipantError:
        raise HTTPException(403, "not a participant") from None
    return DialogWithHistoryOut(
        **_dialog_out(dialog).model_dump(),
        messages={
            "items": [
                {"id": m.id, "dialog_id": m.dialog_id, "author_id": m.author_id,
                 "content": m.content, "created_at": m.created_at}
                for m in items
            ],
            "next_cursor": next_cursor,
        },
    )


@router.get("", response_model=dict)
async def list_dialogs(
    request: Request,
    user: CurrentUser,
    cursor: str | None = None,
    limit: int = Query(50, le=200),
    archived: bool | None = None,
    request_status: Literal["open", "closed"] | None = None,
):
    uc: ListDialogs = request.app.state.list_dialogs
    dialogs, next_cursor = await uc.execute(
        user_id=user, cursor=cursor, limit=limit, archived=archived,
        request_status=RequestStatus(request_status) if request_status else None,
    )
    return {
        "items": [_dialog_out(d).model_dump() for d in dialogs],
        "next_cursor": next_cursor,
    }


@router.patch("/{dialog_id}", response_model=DialogOut)
async def update_dialog(
    dialog_id: UUID, body: UpdateDialogIn, request: Request, user: CurrentUser
):
    uc: UpdateDialog = request.app.state.update_dialog
    participants = None
    if body.participants is not None:
        participants = [Participant(user_id=p.user_id, role=ParticipantRole(p.role))
                        for p in body.participants]
    try:
        dialog = await uc.execute(
            dialog_id=dialog_id,
            actor_id=user,
            request_status=RequestStatus(body.request_status) if body.request_status else None,
            archived=body.archived,
            participants=participants,
        )
    except DialogNotFoundError:
        raise HTTPException(404, "dialog not found") from None
    except NotAParticipantError:
        raise HTTPException(403, "not a participant") from None
    return _dialog_out(dialog)


@router.delete("/{dialog_id}", status_code=204)
async def delete_dialog(dialog_id: UUID, request: Request, user: CurrentUser):
    uc: DeleteDialog = request.app.state.delete_dialog
    try:
        await uc.execute(dialog_id=dialog_id, actor_id=user)
    except DialogNotFoundError:
        raise HTTPException(404, "dialog not found") from None
    except NotAParticipantError:
        raise HTTPException(403, "not a participant") from None
    return Response(status_code=204)