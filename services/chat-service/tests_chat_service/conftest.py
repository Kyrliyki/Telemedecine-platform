"""Shared fakes and helpers for chat-service unit tests."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime, timedelta
from typing import Any
from uuid import UUID, uuid4

import jwt
import pytest

from chat_service.application.ports.request_gateway import RequestInfo
from chat_service.domain.entities import Dialog, DialogUserState, Message, Participant
from chat_service.domain.value_objects import ParticipantRole, RequestStatus

TEST_SECRET = "test-secret-0123456789-0123456789"
TEST_NOW = datetime(2026, 1, 15, 10, 0, tzinfo=UTC)

CLIENT_ID = UUID("11111111-1111-1111-1111-111111111111")
OPERATOR_ID = UUID("22222222-2222-2222-2222-222222222222")
STRANGER_ID = UUID("33333333-3333-3333-3333-333333333333")


def make_dialog(
    *,
    dialog_id: UUID | None = None,
    request_id: str = "req-1",
    status: RequestStatus = RequestStatus.OPEN,
    participants: list[Participant] | None = None,
) -> Dialog:
    return Dialog(
        id=dialog_id or uuid4(),
        request_id=request_id,
        request_status=status,
        participants=participants
        or [
            Participant(user_id=CLIENT_ID, role=ParticipantRole.CLIENT),
            Participant(user_id=OPERATOR_ID, role=ParticipantRole.OPERATOR),
        ],
        created_at=TEST_NOW,
        updated_at=TEST_NOW,
    )


class FakeDialogRepository:
    def __init__(self, dialogs: list[Dialog] | None = None) -> None:
        self.dialogs: dict[UUID, Dialog] = {dialog.id: dialog for dialog in dialogs or []}
        self.touched: list[UUID] = []

    async def get(self, dialog_id: UUID) -> Dialog | None:
        return self.dialogs.get(dialog_id)

    async def get_by_request(self, request_id: str) -> Dialog | None:
        return next(
            (dialog for dialog in self.dialogs.values() if dialog.request_id == request_id),
            None,
        )

    async def create_idempotent(
        self,
        request_id: str,
        participants: list[Participant],
        status: RequestStatus,
    ) -> tuple[Dialog, bool]:
        existing = await self.get_by_request(request_id)
        if existing is not None:
            return existing, False
        dialog = make_dialog(request_id=request_id, status=status, participants=participants)
        self.dialogs[dialog.id] = dialog
        return dialog, True

    async def list_for_user(
        self,
        user_id: UUID,
        *,
        cursor: str | None,
        limit: int,
        archived: bool | None,
        request_status: RequestStatus | None,
    ) -> tuple[list[Dialog], str | None]:
        items = [
            dialog
            for dialog in self.dialogs.values()
            if dialog.is_participant(user_id)
            and (request_status is None or dialog.request_status == request_status)
        ]
        return items[:limit], None

    async def update_status(self, dialog_id: UUID, status: RequestStatus) -> Dialog:
        dialog = self.dialogs[dialog_id]
        dialog.request_status = status
        return dialog

    async def replace_participants(
        self,
        dialog_id: UUID,
        participants: list[Participant],
    ) -> Dialog:
        dialog = self.dialogs[dialog_id]
        dialog.participants = list(participants)
        return dialog

    async def touch(self, dialog_id: UUID) -> None:
        self.touched.append(dialog_id)
        self.dialogs[dialog_id].updated_at = TEST_NOW + timedelta(seconds=1)

    async def delete(self, dialog_id: UUID) -> bool:
        return self.dialogs.pop(dialog_id, None) is not None

    async def last_message(self, dialog_id: UUID):
        return None


class FakeMessageRepository:
    def __init__(self) -> None:
        self.messages: dict[UUID, Message] = {}
        self._by_client_id: dict[tuple[UUID, UUID], Message] = {}

    async def save(
        self,
        *,
        message_id: UUID,
        dialog_id: UUID,
        author_id: UUID,
        content: str,
        client_msg_id: UUID,
    ) -> tuple[Message, bool]:
        existing = self._by_client_id.get((dialog_id, client_msg_id))
        if existing is not None:
            return existing, False
        message = Message(
            id=message_id,
            dialog_id=dialog_id,
            author_id=author_id,
            content=content,
            client_msg_id=client_msg_id,
            created_at=TEST_NOW,
        )
        self.messages[message.id] = message
        self._by_client_id[(dialog_id, client_msg_id)] = message
        return message, True

    async def page(
        self,
        dialog_id: UUID,
        *,
        cursor: UUID | None,
        limit: int,
        before=None,
        after=None,
        since: UUID | None = None,
    ) -> tuple[list[Message], UUID | None]:
        items = [message for message in self.messages.values() if message.dialog_id == dialog_id]
        return items[:limit], None

    async def get(self, message_id: UUID) -> Message | None:
        return self.messages.get(message_id)

    async def edit(self, message_id: UUID, content: str) -> Message:
        self.messages[message_id].content = content
        return self.messages[message_id]

    async def soft_delete(self, message_id: UUID) -> None:
        self.messages[message_id].deleted_at = TEST_NOW


class FakeStateRepository:
    def __init__(self) -> None:
        self.states: dict[tuple[UUID, UUID], DialogUserState] = {}
        self.unread_increments: list[tuple[UUID, UUID]] = []
        self.reads: list[tuple[UUID, UUID, UUID]] = []
        self.archived: list[tuple[UUID, UUID, bool]] = []

    async def get(self, dialog_id: UUID, user_id: UUID) -> DialogUserState | None:
        return self.states.get((dialog_id, user_id))

    async def increment_unread(self, dialog_id: UUID, exclude_user_id: UUID) -> None:
        self.unread_increments.append((dialog_id, exclude_user_id))

    async def mark_read(self, dialog_id: UUID, user_id: UUID, up_to: UUID) -> None:
        self.reads.append((dialog_id, user_id, up_to))

    async def set_archived(self, dialog_id: UUID, user_id: UUID, archived: bool) -> None:
        self.archived.append((dialog_id, user_id, archived))


class RecordingPublisher:
    def __init__(self) -> None:
        self.user_events: list[tuple[UUID, dict[str, Any]]] = []
        self.bulk_events: list[tuple[list[UUID], dict[str, Any]]] = []

    async def publish_to_user(self, user_id: UUID, event: dict[str, Any]) -> None:
        self.user_events.append((user_id, event))

    async def publish_to_users(self, user_ids: list[UUID], event: dict[str, Any]) -> None:
        self.bulk_events.append((user_ids, event))


class RecordingAudit:
    def __init__(self) -> None:
        self.messages: list[dict[str, Any]] = []
        self.events: list[tuple[str, dict[str, Any]]] = []

    def log_message(self, **fields: Any) -> None:
        self.messages.append(fields)

    def log_event(self, event: str, **fields: Any) -> None:
        self.events.append((event, fields))


class AllowRateLimiter:
    async def check(self, user_id: UUID) -> bool:
        return True


class DenyRateLimiter:
    async def check(self, user_id: UUID) -> bool:
        return False


class FakeRequestGateway:
    def __init__(self, info: RequestInfo | None = None) -> None:
        self.info = info
        self.validated: list[str] = []

    async def validate(self, request_id: str) -> RequestInfo | None:
        self.validated.append(request_id)
        return self.info

    async def get_status(self, request_id: str) -> RequestStatus:
        return self.info.status if self.info else RequestStatus.OPEN


@dataclass
class ChatTestContext:
    dialogs: FakeDialogRepository
    messages: FakeMessageRepository
    states: FakeStateRepository
    publisher: RecordingPublisher
    audit: RecordingAudit
    gateway: FakeRequestGateway
    rate_limiter: Any = field(default_factory=AllowRateLimiter)


def build_context(
    *,
    dialog: Dialog | None = None,
    request_info: RequestInfo | None = None,
    allowed: bool = True,
) -> ChatTestContext:
    return ChatTestContext(
        dialogs=FakeDialogRepository([dialog] if dialog else []),
        messages=FakeMessageRepository(),
        states=FakeStateRepository(),
        publisher=RecordingPublisher(),
        audit=RecordingAudit(),
        gateway=FakeRequestGateway(request_info),
        rate_limiter=AllowRateLimiter() if allowed else DenyRateLimiter(),
    )


def make_token(
    subject: UUID | str,
    *,
    secret: str = TEST_SECRET,
    algorithm: str = "HS256",
    issuer: str | None = None,
    audience: str | None = None,
    expires_in: timedelta = timedelta(hours=1),
) -> str:
    now = datetime.now(UTC)
    payload: dict[str, Any] = {
        "sub": str(subject),
        "iat": now,
        "exp": now + expires_in,
    }
    if issuer:
        payload["iss"] = issuer
    if audience:
        payload["aud"] = audience
    return jwt.encode(payload, secret, algorithm=algorithm)


@pytest.fixture
def context() -> ChatTestContext:
    return build_context(dialog=make_dialog())
