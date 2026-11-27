from uuid import UUID, uuid4

from chat_service.application.ports.audit_logger import AuditLogger
from chat_service.application.ports.dialog_repository import DialogRepository
from chat_service.application.ports.message_repository import MessageRepository
from chat_service.application.ports.publisher import EventPublisher
from chat_service.application.ports.rate_limiter import RateLimiter
from chat_service.application.ports.state_repository import DialogStateRepository
from chat_service.domain.entities import Message
from chat_service.domain.exceptions import (
    DialogClosedError,
    DialogNotFoundError,
    NotAParticipantError,
    RateLimitedError,
    ValidationError,
)
from chat_service.domain.value_objects import MessageContent


class SendMessage:
    def __init__(
        self,
        dialogs: DialogRepository,
        messages: MessageRepository,
        states: DialogStateRepository,
        publisher: EventPublisher,
        audit: AuditLogger,
        rate_limiter: RateLimiter,
    ) -> None:
        self._dialogs = dialogs
        self._messages = messages
        self._states = states
        self._publisher = publisher
        self._audit = audit
        self._rate_limiter = rate_limiter

    async def execute(
        self,
        *,
        dialog_id: UUID,
        author_id: UUID,
        content: str,
        client_msg_id: UUID | None = None,
    ) -> tuple[Message, bool]:  
        if not await self._rate_limiter.check(author_id):
            raise RateLimitedError()

        try:
            MessageContent(content)
        except ValueError as e:
            raise ValidationError(str(e)) from e

        dialog = await self._dialogs.get(dialog_id)
        if dialog is None:
            raise DialogNotFoundError()
        if not dialog.is_participant(author_id):
            raise NotAParticipantError()
        if not dialog.is_open():
            raise DialogClosedError()

        message, is_new = await self._messages.save(
            message_id=uuid4(),
            dialog_id=dialog_id,
            author_id=author_id,
            content=content.strip(),
            client_msg_id=client_msg_id or uuid4(),
        )

        if not is_new:
            return message, False

        await self._dialogs.touch(dialog_id)
        await self._states.increment_unread(dialog_id, exclude_user_id=author_id)

        recipients = [p.user_id for p in dialog.participants if p.user_id != author_id]
        self._audit.log_message(
            message_id=message.id,
            dialog_id=dialog_id,
            external_request_id=dialog.request_id,
            author_id=author_id,
            recipient_ids=recipients,
            content=message.content,
            created_at=message.created_at.isoformat(),
        )

        event = {
            "type": "message.new",
            "payload": {
                "id": str(message.id),
                "dialog_id": str(dialog_id),
                "request_id": dialog.request_id,
                "author_id": str(author_id),
                "content": message.content,
                "created_at": message.created_at.isoformat(),
                "client_msg_id": str(message.client_msg_id),
            },
        }
        await self._publisher.publish_to_users(recipients, event)
        return message, True