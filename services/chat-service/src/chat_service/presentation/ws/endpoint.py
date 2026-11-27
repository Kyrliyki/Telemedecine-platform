import json
import logging
from uuid import UUID

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from chat_service.application.ports.presence import PresenceService
from chat_service.application.use_cases.mark_read import MarkRead
from chat_service.application.use_cases.send_message import SendMessage
from chat_service.application.use_cases.send_typing import SendTyping
from chat_service.config import get_settings
from chat_service.domain.exceptions import DomainError
from chat_service.infrastructure.auth.jwt import get_jwt_validator

log = logging.getLogger("chat.ws")
router = APIRouter()

def _err(code: str, message: str) -> dict:
    return {"type": "error", "payload": {"code": code, "message": message}}

@router.websocket("/ws")
async def ws_endpoint(
    ws: WebSocket,
    token: str | None = None,
    user_id: UUID | None = None,
):
    settings = get_settings()

    if settings.auth_disabled:
        
        user_id = user_id or settings.test_user_id
    else:
        if not token:
            await ws.close(code=4001, reason="UNAUTHORIZED")
            return
        try:
            payload = await get_jwt_validator().validate(token)
            user_id = UUID(payload.subject)
        except Exception:
            await ws.close(code=4001, reason="UNAUTHORIZED")
            return

    app = ws.app
    manager = app.state.ws_manager
    presence: PresenceService = app.state.presence
    send_uc: SendMessage = app.state.send_message
    read_uc: MarkRead = app.state.mark_read

    await manager.connect(user_id, ws)
    await presence.set_online(user_id)

    try:
        while True:
            raw = await ws.receive_text()
            try:
                data = json.loads(raw)
                mtype = data["type"]
                p = data.get("payload", {})
            except Exception:
                await ws.send_text(json.dumps(_err("VALIDATION_ERROR", "bad frame")))
                continue

            if mtype == "ping":
                await ws.send_text(json.dumps({"type": "pong"}))
                continue

            if mtype == "message.send":
                dialog_id = UUID(p["dialog_id"])
                client_msg_id = UUID(p["client_msg_id"]) if p.get("client_msg_id") else None
                try:
                    msg, _is_new = await send_uc.execute(
                        dialog_id=dialog_id,
                        author_id=user_id,
                        content=p["content"],
                        client_msg_id=client_msg_id,
                    )
                    ack = {
                        "type": "message.ack",
                        "payload": {
                            "client_msg_id": str(msg.client_msg_id),
                            "id": str(msg.id),
                            "created_at": msg.created_at.isoformat(),
                        },
                    }
                    await manager.send_direct(user_id, ack)
                except DomainError as e:
                    await manager.send_direct(user_id, _err(e.code, str(e) or e.code))
                continue

            if mtype == "read":
                try:
                    await read_uc.execute(
                        dialog_id=UUID(p["dialog_id"]),
                        user_id=user_id,
                        up_to=UUID(p["up_to_message_id"]),
                    )
                except DomainError as e:
                    await manager.send_direct(user_id, _err(e.code, str(e) or e.code))
                continue

            if mtype == "typing":
                try:
                    typing_uc: SendTyping = app.state.send_typing
                    await typing_uc.execute(
                        dialog_id=UUID(p["dialog_id"]),
                        user_id=user_id,
                    )
                except DomainError as e:
                    await manager.send_direct(user_id, _err(e.code, str(e) or e.code))
                continue

            await ws.send_text(json.dumps(_err("VALIDATION_ERROR", f"unknown type {mtype}")))
    except WebSocketDisconnect:
        pass
    finally:
        await presence.set_offline(user_id)
        await manager.disconnect(user_id, ws)