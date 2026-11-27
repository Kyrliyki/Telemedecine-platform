from __future__ import annotations

import uuid
from datetime import UTC, datetime

from sqlalchemy import delete, func, select, update
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from chat_service.domain.entities import Dialog, Message, Participant
from chat_service.domain.value_objects import RequestStatus
from chat_service.infrastructure.db.models import (
    DialogModel,
    DialogParticipantModel,
    DialogUserStateModel,
    MessageModel,
)


def _to_dialog(m: DialogModel, parts: list[DialogParticipantModel]) -> Dialog:
    return Dialog(
        id=m.id,
        request_id=m.request_id,
        request_status=m.request_status,
        participants=[Participant(user_id=p.user_id, role=p.role) for p in parts],
        created_at=m.created_at,
        updated_at=m.updated_at,
    )

class SqlDialogRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._s = session

    async def _parts(self, dialog_id: uuid.UUID) -> list[DialogParticipantModel]:
        r = await self._s.execute(
            select(DialogParticipantModel).where(DialogParticipantModel.dialog_id == dialog_id)
        )
        return list(r.scalars())

    async def get(self, dialog_id: uuid.UUID) -> Dialog | None:
        r = await self._s.execute(select(DialogModel).where(DialogModel.id == dialog_id))
        m = r.scalar_one_or_none()
        return _to_dialog(m, await self._parts(dialog_id)) if m else None

    async def get_by_request(self, request_id: str) -> Dialog | None:
        r = await self._s.execute(select(DialogModel).where(DialogModel.request_id == request_id))
        m = r.scalar_one_or_none()
        return _to_dialog(m, await self._parts(m.id)) if m else None

    async def is_participant(self, dialog_id: uuid.UUID, user_id: uuid.UUID) -> bool:
        r = await self._s.execute(
            select(DialogParticipantModel.user_id).where(
                DialogParticipantModel.dialog_id == dialog_id,
                DialogParticipantModel.user_id == user_id,
            )
        )
        return r.scalar_one_or_none() is not None

    async def create_idempotent(
        self,
        request_id: str,
        participants: list[Participant],
        status: RequestStatus,
    ) -> tuple[Dialog, bool]:
        new_id = uuid.uuid4()
        stmt = (
            pg_insert(DialogModel)
            .values(id=new_id, request_id=request_id, request_status=status)
            .on_conflict_do_nothing(index_elements=[DialogModel.request_id])
            .returning(DialogModel.id)
        )
        res = await self._s.execute(stmt)
        inserted_id = res.scalar_one_or_none()
        created = inserted_id is not None
        dialog_id = inserted_id or (
            await self._s.execute(
                select(DialogModel.id).where(DialogModel.request_id == request_id)
            )
        ).scalar_one()

        if created and participants:
            await self._s.execute(
                pg_insert(DialogParticipantModel)
                .values([
                    {"dialog_id": dialog_id, "user_id": p.user_id, "role": p.role}
                    for p in participants
                ])
                .on_conflict_do_nothing()
            )
            
            await self._s.execute(
                pg_insert(DialogUserStateModel)
                .values([{"dialog_id": dialog_id, "user_id": p.user_id} for p in participants])
                .on_conflict_do_nothing()
            )

        await self._s.commit()
        dialog = await self.get(dialog_id)
        assert dialog is not None
        return dialog, created

    async def list_for_user(
        self,
        user_id: uuid.UUID,
        *,
        cursor: str | None,
        limit: int,
        archived: bool | None,
        request_status: RequestStatus | None,
    ) -> tuple[list[Dialog], str | None]:
        q = (
            select(DialogModel)
            .join(DialogParticipantModel, DialogParticipantModel.dialog_id == DialogModel.id)
            .where(DialogParticipantModel.user_id == user_id)
        )
        if request_status is not None:
            q = q.where(DialogModel.request_status == request_status)
        if archived is not None:
            sub = select(DialogUserStateModel.dialog_id).where(
                DialogUserStateModel.user_id == user_id,
                DialogUserStateModel.archived == archived,
            )
            q = q.where(DialogModel.id.in_(sub))
        if cursor:
            cur_ts = datetime.fromisoformat(cursor)
            q = q.where(DialogModel.updated_at < cur_ts)
        q = q.order_by(DialogModel.updated_at.desc()).limit(limit + 1)

        rows = list((await self._s.execute(q)).scalars())
        next_cursor = rows[limit - 1].updated_at.isoformat() if len(rows) > limit else None
        rows = rows[:limit]
        dialogs = [_to_dialog(m, await self._parts(m.id)) for m in rows]
        return dialogs, next_cursor

    async def update_status(self, dialog_id: uuid.UUID, status: RequestStatus) -> Dialog:
        await self._s.execute(
            update(DialogModel)
            .where(DialogModel.id == dialog_id)
            .values(request_status=status, updated_at=func.now())
        )
        await self._s.commit()
        d = await self.get(dialog_id)
        assert d is not None
        return d

    async def replace_participants(
        self, dialog_id: uuid.UUID, participants: list[Participant]
    ) -> Dialog:
        await self._s.execute(
            DialogParticipantModel.__table__.delete().where(
                DialogParticipantModel.dialog_id == dialog_id
            )
        )
        await self._s.execute(
            pg_insert(DialogParticipantModel).values([
                {"dialog_id": dialog_id, "user_id": p.user_id, "role": p.role}
                for p in participants
            ])
        )
        await self._s.commit()
        d = await self.get(dialog_id)
        assert d is not None
        return d

    async def touch(self, dialog_id: uuid.UUID) -> None:
        await self._s.execute(
            update(DialogModel).where(DialogModel.id == dialog_id).values(updated_at=func.now())
        )
        await self._s.commit()

    async def delete(self, dialog_id: uuid.UUID) -> bool:
        result = await self._s.execute(
            delete(DialogModel).where(DialogModel.id == dialog_id)
        )
        await self._s.commit()
        return bool(result.rowcount)

    async def last_message(self, dialog_id: uuid.UUID) -> Message | None:
        r = await self._s.execute(
            select(MessageModel)
            .where(MessageModel.dialog_id == dialog_id, MessageModel.deleted_at.is_(None))
            .order_by(MessageModel.created_at.desc())
            .limit(1)
        )
        m = r.scalar_one_or_none()
        return Message(
            id=m.id, dialog_id=m.dialog_id, author_id=m.author_id, content=m.content,
            client_msg_id=m.client_msg_id, created_at=m.created_at,
            edited_at=m.edited_at, deleted_at=m.deleted_at,
        ) if m else None

class SqlMessageRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._s = session

    async def save(
        self, *, message_id: uuid.UUID, dialog_id: uuid.UUID, author_id: uuid.UUID,
        content: str, client_msg_id: uuid.UUID,
    ) -> tuple[Message, bool]:
        stmt = (
            pg_insert(MessageModel)
            .values(
                id=message_id, dialog_id=dialog_id, author_id=author_id,
                content=content, client_msg_id=client_msg_id,
            )
            .on_conflict_do_nothing(constraint="uq_message_dedup")
            .returning(MessageModel.id)
        )
        inserted = (await self._s.execute(stmt)).scalar_one_or_none()
        await self._s.commit()
        if inserted is not None:
            return Message(
                id=inserted, dialog_id=dialog_id, author_id=author_id, content=content,
                client_msg_id=client_msg_id, created_at=datetime.now(UTC),
            ), True
        
        r = await self._s.execute(
            select(MessageModel).where(
                MessageModel.dialog_id == dialog_id,
                MessageModel.author_id == author_id,
                MessageModel.client_msg_id == client_msg_id,
            )
        )
        m = r.scalar_one()
        return Message(
            id=m.id, dialog_id=m.dialog_id, author_id=m.author_id, content=m.content,
            client_msg_id=m.client_msg_id, created_at=m.created_at,
            edited_at=m.edited_at, deleted_at=m.deleted_at,
        ), False

    async def page(
        self, dialog_id: uuid.UUID, *, cursor: uuid.UUID | None, limit: int,
        before=None, after=None, since: uuid.UUID | None = None,
    ) -> tuple[list[Message], uuid.UUID | None]:
        q = select(MessageModel).where(
            MessageModel.dialog_id == dialog_id, MessageModel.deleted_at.is_(None)
        )
        if since:
            sub = await self._s.execute(
                select(MessageModel.created_at).where(MessageModel.id == since)
            )
            since_ts = sub.scalar_one_or_none()
            if since_ts:
                q = q.where(MessageModel.created_at > since_ts)
        if cursor:
            sub = await self._s.execute(
                select(MessageModel.created_at).where(MessageModel.id == cursor)
            )
            c_ts = sub.scalar_one_or_none()
            if c_ts:
                q = q.where(MessageModel.created_at < c_ts)
        q = q.order_by(MessageModel.created_at.desc()).limit(limit + 1)
        rows = list((await self._s.execute(q)).scalars())
        next_cursor = rows[limit - 1].id if len(rows) > limit else None
        rows = rows[:limit]
        return [
            Message(
                id=m.id, dialog_id=m.dialog_id, author_id=m.author_id, content=m.content,
                client_msg_id=m.client_msg_id, created_at=m.created_at,
                edited_at=m.edited_at, deleted_at=m.deleted_at,
            ) for m in rows
        ], next_cursor

    async def get(self, message_id: uuid.UUID) -> Message | None:
        r = await self._s.execute(select(MessageModel).where(MessageModel.id == message_id))
        m = r.scalar_one_or_none()
        if not m:
            return None
        return Message(
            id=m.id, dialog_id=m.dialog_id, author_id=m.author_id, content=m.content,
            client_msg_id=m.client_msg_id, created_at=m.created_at,
            edited_at=m.edited_at, deleted_at=m.deleted_at,
        )

    async def edit(self, message_id: uuid.UUID, content: str) -> Message:
        await self._s.execute(
            update(MessageModel)
            .where(MessageModel.id == message_id)
            .values(content=content, edited_at=func.now())
        )
        await self._s.commit()
        m = await self.get(message_id)
        assert m is not None
        return m

    async def soft_delete(self, message_id: uuid.UUID) -> None:
        await self._s.execute(
            update(MessageModel)
            .where(MessageModel.id == message_id)
            .values(deleted_at=func.now())
        )
        await self._s.commit()

class SqlDialogStateRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._s = session

    async def get(self, dialog_id, user_id):
        r = await self._s.execute(
            select(DialogUserStateModel).where(
                DialogUserStateModel.dialog_id == dialog_id,
                DialogUserStateModel.user_id == user_id,
            )
        )
        return r.scalar_one_or_none()

    async def increment_unread(self, dialog_id, exclude_user_id) -> None:
        await self._s.execute(
            update(DialogUserStateModel)
            .where(
                DialogUserStateModel.dialog_id == dialog_id,
                DialogUserStateModel.user_id != exclude_user_id,
            )
            .values(unread_count=DialogUserStateModel.unread_count + 1, updated_at=func.now())
        )
        await self._s.commit()

    async def mark_read(self, dialog_id, user_id, up_to) -> None:
        await self._s.execute(
            update(DialogUserStateModel)
            .where(
                DialogUserStateModel.dialog_id == dialog_id,
                DialogUserStateModel.user_id == user_id,
            )
            .values(last_read_message_id=up_to, unread_count=0, updated_at=func.now())
        )
        await self._s.commit()

    async def set_archived(self, dialog_id, user_id, archived) -> None:
        await self._s.execute(
            update(DialogUserStateModel)
            .where(
                DialogUserStateModel.dialog_id == dialog_id,
                DialogUserStateModel.user_id == user_id,
            )
            .values(archived=archived, updated_at=func.now())
        )
        await self._s.commit()