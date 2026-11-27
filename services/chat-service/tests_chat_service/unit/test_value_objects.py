"""Domain value-object tests."""

from __future__ import annotations

import pytest

from chat_service.domain.value_objects import MessageContent, RequestId


@pytest.mark.parametrize("value", ["req-1", "REQ_2", "a.b:c"])
def test_request_id_accepts_valid_values(value: str) -> None:
    assert RequestId(value).value == value


@pytest.mark.parametrize("value", ["", "has space", "x" * 256, "bad/slash"])
def test_request_id_rejects_invalid_values(value: str) -> None:
    with pytest.raises(ValueError):
        RequestId(value)


def test_message_content_rejects_blank_text() -> None:
    with pytest.raises(ValueError):
        MessageContent("   ")


def test_message_content_rejects_over_limit() -> None:
    with pytest.raises(ValueError):
        MessageContent("x" * 4097)


def test_message_content_accepts_normal_text() -> None:
    assert MessageContent("  hello  ").value == "  hello  "
