"""Outbox relay and inbound event handler tests."""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Any
from uuid import uuid4

from file_service.application.dto import Actor, UploadFileCommand
from file_service.infrastructure.messaging.handlers import ChatEventsHandler
from file_service.infrastructure.messaging.outbox_relay import OutboxRelay
from tests_file_service.fakes import FakeContext, collect_stream


class RecordingPublisher:
    def __init__(self, *, fail: bool = False) -> None:
        self.fail = fail
        self.published: list[tuple[str, dict[str, Any]]] = []

    async def publish(self, routing_key: str, envelope: dict[str, Any]) -> None:
        if self.fail:
            raise ConnectionError("rabbit is down")
        self.published.append((routing_key, envelope))


class StaticClock:
    def now(self) -> datetime:
        return datetime(2026, 1, 1, tzinfo=UTC)


async def test_outbox_relay_publishes_and_marks(fake_context: FakeContext) -> None:
    view = await fake_context.use_cases.upload_file(
        UploadFileCommand(
            actor=Actor(id=uuid4(), roles=frozenset({"patient"})),
            chat_id=uuid4(),
            file_name="a.pdf",
            content_type="application/pdf",
            stream=collect_stream(b"data"),
            declared_size=4,
        )
    )
    publisher = RecordingPublisher()
    relay = OutboxRelay(
        uow_factory=fake_context.db.uow_factory,
        publisher=publisher,  # type: ignore[arg-type]
        clock=StaticClock(),  # type: ignore[arg-type]
    )

    processed, failures = await relay.run_once()

    assert (processed, failures) == (1, 0)
    assert publisher.published[0][0] == "file.uploaded"
    assert publisher.published[0][1]["payload"]["file_id"] == str(view.info.id)
    assert fake_context.db.outbox == []


async def test_outbox_relay_records_failure(fake_context: FakeContext) -> None:
    await fake_context.use_cases.upload_file(
        UploadFileCommand(
            actor=Actor(id=uuid4()),
            chat_id=uuid4(),
            file_name="a.pdf",
            content_type="application/pdf",
            stream=collect_stream(b"data"),
            declared_size=4,
        )
    )
    publisher = RecordingPublisher(fail=True)
    relay = OutboxRelay(
        uow_factory=fake_context.db.uow_factory,
        publisher=publisher,  # type: ignore[arg-type]
        clock=StaticClock(),  # type: ignore[arg-type]
    )

    processed, failures = await relay.run_once()

    assert (processed, failures) == (0, 1)
    assert fake_context.db.outbox[0].attempts == 1


async def test_chat_deleted_event_purges_files(fake_context: FakeContext) -> None:
    chat_id = uuid4()
    view = await fake_context.use_cases.upload_file(
        UploadFileCommand(
            actor=Actor(id=uuid4()),
            chat_id=chat_id,
            file_name="a.pdf",
            content_type="application/pdf",
            stream=collect_stream(b"data"),
            declared_size=4,
        )
    )
    handler = ChatEventsHandler(fake_context.use_cases.purge_chat_files)

    await handler(
        {
            "event_type": "chat.deleted",
            "payload": {"chat_id": str(chat_id)},
        }
    )

    attachment = fake_context.db.attachments[view.info.id]
    assert attachment.status.value == "deleted"
    assert view.info.storage_key in fake_context.db.storage.deleted


async def test_unknown_chat_event_is_ignored(fake_context: FakeContext) -> None:
    handler = ChatEventsHandler(fake_context.use_cases.purge_chat_files)
    await handler({"event_type": "chat.created", "payload": {}})
    assert fake_context.db.outbox == []
