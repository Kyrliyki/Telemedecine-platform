"""Smoke tests for the file-service DTOs."""

from __future__ import annotations

import uuid

from file_service.application.dto import Actor
from file_service.application.ports import StoredObject


def test_placeholder_actor() -> None:
    actor = Actor(id=uuid.uuid4())
    assert actor.roles == frozenset()
    assert isinstance(StoredObject(1), StoredObject)
