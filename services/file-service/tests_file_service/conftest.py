"""Shared test fixtures."""

from __future__ import annotations

import pytest

from tests_file_service.fakes import FakeContext, build_fake_context


@pytest.fixture
def fake_context() -> FakeContext:
    return build_fake_context()
