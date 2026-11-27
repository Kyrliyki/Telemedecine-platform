"""API-level fixtures for chat-service: real HTTP routes, fakes for ports."""

from __future__ import annotations

import pytest
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient

from chat_service.application.use_cases.create_or_get_dialog import CreateOrGetDialog
from chat_service.application.use_cases.delete_dialog import DeleteDialog
from chat_service.application.use_cases.get_dialog_by_request import GetDialogByRequest
from chat_service.application.use_cases.get_history import GetHistory
from chat_service.application.use_cases.list_dialogs import ListDialogs
from chat_service.application.use_cases.mark_read import MarkRead
from chat_service.application.use_cases.update_dialog import UpdateDialog
from chat_service.presentation.api import routes_dialogs, routes_messages
from chat_service.presentation.api.deps import current_user
from tests_chat_service.conftest import CLIENT_ID


@pytest.fixture
def app(context) -> FastAPI:
    app = FastAPI()
    app.state.create_or_get_dialog = CreateOrGetDialog(
        context.dialogs, context.gateway, context.audit
    )
    app.state.get_dialog_by_request = GetDialogByRequest(
        context.dialogs, context.messages, context.states
    )
    app.state.list_dialogs = ListDialogs(context.dialogs)
    app.state.get_history = GetHistory(context.dialogs, context.messages)
    app.state.mark_read = MarkRead(context.dialogs, context.states, context.publisher)
    app.state.update_dialog = UpdateDialog(
        context.dialogs, context.states, context.publisher, context.audit
    )
    app.state.delete_dialog = DeleteDialog(context.dialogs, None, context.audit)
    app.include_router(routes_dialogs.router)
    app.include_router(routes_messages.router)
    app.dependency_overrides[current_user] = lambda: CLIENT_ID
    return app


@pytest.fixture
async def client(app: FastAPI):
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as http_client:
        yield http_client
