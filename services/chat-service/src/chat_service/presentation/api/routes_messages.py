from uuid import UUID

from fastapi import APIRouter, HTTPException, Query, Request, Response

from chat_service.application.use_cases.get_history import GetHistory
from chat_service.application.use_cases.mark_read import MarkRead
from chat_service.config import get_settings
from chat_service.domain.exceptions import DialogNotFoundError, NotAParticipantError
from chat_service.presentation.api.deps import CurrentUser
from chat_service.presentation.api.schemas import MarkReadIn

router = APIRouter(prefix="/api/v1/dialogs", tags=["messages"])


@router.get("/{dialog_id}/messages")
async def get_history(
    dialog_id: UUID, request: Request, user: CurrentUser,
    cursor: UUID | None = None,
    since: UUID | None = None,
    limit: int = Query(None),
):
    s = get_settings()
    effective_limit = min(limit or s.history_default_limit, s.history_max_limit)
    uc: GetHistory = request.app.state.get_history
    try:
        items, next_cursor = await uc.execute(
            dialog_id=dialog_id, user_id=user, cursor=cursor,
            limit=effective_limit, since=since,
        )
    except DialogNotFoundError:
        raise HTTPException(404, "dialog not found") from None
    except NotAParticipantError:
        raise HTTPException(403, "not a participant") from None
    return {
        "items": [
            {"id": m.id, "dialog_id": m.dialog_id, "author_id": m.author_id,
             "content": m.content, "created_at": m.created_at,
             "edited_at": m.edited_at, "deleted_at": m.deleted_at}
            for m in items
        ],
        "next_cursor": next_cursor,
    }


@router.post("/{dialog_id}/read", status_code=204)
async def mark_read(dialog_id: UUID, body: MarkReadIn, request: Request, user: CurrentUser):
    uc: MarkRead = request.app.state.mark_read
    try:
        await uc.execute(dialog_id=dialog_id, user_id=user, up_to=body.up_to_message_id)
    except DialogNotFoundError:
        raise HTTPException(404, "dialog not found") from None
    except NotAParticipantError:
        raise HTTPException(403, "not a participant") from None
    return Response(status_code=204)