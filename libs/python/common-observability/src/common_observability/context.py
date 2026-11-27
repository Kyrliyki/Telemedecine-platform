from __future__ import annotations

from contextvars import ContextVar
from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class RequestContext:
    request_id: str
    correlation_id: str


_current: ContextVar[RequestContext | None] = ContextVar(
    "telemed_request_context", default=None
)


def set_request_context(context: RequestContext) -> object:
    return _current.set(context)


def reset_request_context(token: object) -> None:
    _current.reset(token)  # type: ignore[arg-type]


def get_request_context() -> RequestContext | None:
    return _current.get()


def outbound_context_headers() -> dict[str, str]:
    context = _current.get()
    if context is None:
        return {}
    return {
        "X-Request-Id": context.request_id,
        "X-Correlation-Id": context.correlation_id,
    }
