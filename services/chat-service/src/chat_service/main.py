import logging
from contextlib import asynccontextmanager

import redis.asyncio as aioredis
from common_observability import configure_logging, install_metrics
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from chat_service.application.use_cases.create_or_get_dialog import CreateOrGetDialog
from chat_service.application.use_cases.delete_dialog import DeleteDialog
from chat_service.application.use_cases.get_dialog_by_request import GetDialogByRequest
from chat_service.application.use_cases.get_history import GetHistory
from chat_service.application.use_cases.list_dialogs import ListDialogs
from chat_service.application.use_cases.mark_read import MarkRead
from chat_service.application.use_cases.send_message import SendMessage
from chat_service.application.use_cases.send_typing import SendTyping
from chat_service.application.use_cases.update_dialog import UpdateDialog
from chat_service.config import get_settings
from chat_service.domain.exceptions import (
    DomainError,
)
from chat_service.infrastructure.db.repositories import (
    SqlDialogRepository,
    SqlDialogStateRepository,
    SqlMessageRepository,
)
from chat_service.infrastructure.db.session import init_engine
from chat_service.infrastructure.logging.audit import JsonAuditLogger
from chat_service.infrastructure.messaging.chat_events import RabbitChatEventsPublisher
from chat_service.infrastructure.redis.presence import RedisPresenceService
from chat_service.infrastructure.redis.publisher import RedisEventPublisher
from chat_service.infrastructure.redis.rate_limiter import RedisSlidingWindowRateLimiter
from chat_service.infrastructure.requests.http_gateway import HttpRequestGateway, NoopRequestGateway
from chat_service.presentation.api import health, routes_dialogs, routes_internal, routes_messages
from chat_service.presentation.ws.endpoint import router as ws_router
from chat_service.presentation.ws.manager import ConnectionManager

log = logging.getLogger("chat")

@asynccontextmanager
async def lifespan(app: FastAPI):
    s = get_settings()
    configure_logging("chat-service", level=s.log_level)
    init_engine()

    if s.auth_disabled:
        log.warning(
            "AUTH_DISABLED=true: JWT validation is bypassed. "
            "Use this mode for local development and tests only."
        )

    redis = aioredis.from_url(s.redis_url, decode_responses=True)
    app.state.redis = redis

    
    from chat_service.infrastructure.db.session import _session_factory
    app.state.session_factory = _session_factory

    gateway_url = s.appointment_service_url or s.request_service_url
    gateway = HttpRequestGateway(gateway_url, s.internal_service_token) if (
        s.request_gateway_enabled and gateway_url
    ) else NoopRequestGateway()
    audit = JsonAuditLogger()
    publisher = RedisEventPublisher(redis)
    chat_events = (
        RabbitChatEventsPublisher(url=s.rabbitmq_url, exchange_name=s.rabbitmq_events_exchange)
        if s.rabbitmq_url
        else None
    )
    app.state.chat_events = chat_events
    presence = RedisPresenceService(redis)
    rate_limiter = RedisSlidingWindowRateLimiter(redis, s.rate_limit_per_min)

    
    
    def build_ucs(session):
        dialogs = SqlDialogRepository(session)
        messages = SqlMessageRepository(session)
        states = SqlDialogStateRepository(session)
        return {
            "create_or_get_dialog": CreateOrGetDialog(dialogs, gateway, audit),
            "get_dialog_by_request": GetDialogByRequest(dialogs, messages, states),
            "list_dialogs": ListDialogs(dialogs),
            "send_message": SendMessage(dialogs, messages, states, publisher, audit, rate_limiter),
            "send_typing": SendTyping(dialogs, publisher),
            "get_history": GetHistory(dialogs, messages),
            "mark_read": MarkRead(dialogs, states, publisher),
            "update_dialog": UpdateDialog(dialogs, states, publisher, audit),
            "delete_dialog": DeleteDialog(dialogs, chat_events, audit),
        }

    
    session = _session_factory()
    ucs = build_ucs(session)
    app.state.send_message = ucs["send_message"]
    app.state.send_typing = ucs["send_typing"]
    app.state.mark_read = ucs["mark_read"]
    app.state.publisher = publisher
    app.state.presence = presence
    app.state.ws_manager = ConnectionManager(redis)

    yield

    await session.close()
    if chat_events is not None:
        await chat_events.aclose()
    from chat_service.infrastructure.auth.jwt import get_jwt_validator
    await get_jwt_validator().aclose()
    await redis.aclose()

app = FastAPI(title="chat-service", version="0.1.0", lifespan=lifespan)
app.state.settings = get_settings()

@app.middleware("http")
async def attach_ucs(request: Request, call_next):
    from chat_service.infrastructure.db.session import _session_factory
    async with _session_factory() as session:
        from chat_service.infrastructure.logging.audit import JsonAuditLogger
        from chat_service.infrastructure.redis.publisher import RedisEventPublisher
        from chat_service.infrastructure.redis.rate_limiter import RedisSlidingWindowRateLimiter
        from chat_service.infrastructure.requests.http_gateway import (
            HttpRequestGateway,
            NoopRequestGateway,
        )
        s = get_settings()
        gateway_url = s.appointment_service_url or s.request_service_url
        gateway = HttpRequestGateway(gateway_url, s.internal_service_token) if (
            s.request_gateway_enabled and gateway_url
        ) else NoopRequestGateway()
        audit = JsonAuditLogger()
        publisher = RedisEventPublisher(request.app.state.redis)
        rate = RedisSlidingWindowRateLimiter(request.app.state.redis, s.rate_limit_per_min)
        dialogs = SqlDialogRepository(session)
        messages = SqlMessageRepository(session)
        states = SqlDialogStateRepository(session)
        request.app.state.create_or_get_dialog = CreateOrGetDialog(dialogs, gateway, audit)
        request.app.state.get_dialog_by_request = GetDialogByRequest(dialogs, messages, states)
        request.app.state.list_dialogs = ListDialogs(dialogs)
        request.app.state.get_history = GetHistory(dialogs, messages)
        request.app.state.mark_read = MarkRead(dialogs, states, publisher)
        request.app.state.update_dialog = UpdateDialog(dialogs, states, publisher, audit)
        request.app.state.delete_dialog = DeleteDialog(
            dialogs, request.app.state.chat_events, audit
        )
        request.app.state.send_message = SendMessage(
            dialogs, messages, states, publisher, audit, rate
        )
        request.app.state.send_typing = SendTyping(dialogs, publisher)
        return await call_next(request)

_settings = get_settings()
app.add_middleware(
    CORSMiddleware,
    allow_origins=_settings.cors_origins_list,
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
    expose_headers=["*"],
)

@app.exception_handler(DomainError)
async def domain_exception_handler(request: Request, exc: DomainError):
    mapping = {
        "DIALOG_NOT_FOUND": 404,
        "FORBIDDEN": 403,
        "DIALOG_CLOSED": 409,
        "VALIDATION_ERROR": 422,
        "RATE_LIMITED": 429,
        "REQUEST_NOT_FOUND": 404,
        "REQUEST_SERVICE_UNAVAILABLE": 503,
    }
    status = mapping.get(exc.code, 400)
    return JSONResponse(status_code=status, content={"code": exc.code, "message": str(exc)})

app.include_router(health.router)
app.include_router(routes_dialogs.router)
app.include_router(routes_messages.router)
app.include_router(routes_internal.router)
app.include_router(ws_router)

@app.get("/v3/api-docs", include_in_schema=False)
async def openapi_alias():
    """Springdoc-compatible alias used by the Java API Gateway."""
    return JSONResponse(app.openapi())

install_metrics(app, "chat-service")