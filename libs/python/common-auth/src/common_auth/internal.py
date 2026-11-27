"""Service-to-service authentication for ``/internal/**`` endpoints.

Java services call Python ``/internal/**`` endpoints with a shared
``X-Internal-Token`` header. The token is optional in local development, but
the guard is **fail-closed**: when no token is configured the endpoint reports
503 instead of silently allowing anonymous internal calls.
"""

from __future__ import annotations

import hmac

from fastapi import Header, HTTPException, Request, status
from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict

INTERNAL_TOKEN_HEADER = "X-Internal-Token"


class InternalTokenSettings(BaseSettings):
    model_config = SettingsConfigDict(extra="ignore")

    token: str = Field(
        default="",
        validation_alias=AliasChoices(
            "INTERNAL_SERVICE_TOKEN",
            "INTERNAL_API_TOKEN",
            "APP_INTERNAL_SERVICE_TOKEN",
        ),
    )
    header_name: str = INTERNAL_TOKEN_HEADER


def internal_token_guard(
    expected_token: str | None = None,
    *,
    settings_attribute: str = "internal_service_token",
):
    """Build a FastAPI dependency that validates ``X-Internal-Token``.

    Pass an explicit token when it is already known, or leave ``expected_token``
    empty to read it from ``request.app.state.settings``. The guard is
    fail-closed: an unconfigured token rejects the call with 503.
    """

    async def guard(
        request: Request,
        x_internal_token: str | None = Header(
            default=None,
            alias=INTERNAL_TOKEN_HEADER,
        ),
    ) -> None:
        token = expected_token
        if token is None:
            settings = getattr(request.app.state, "settings", None)
            token = getattr(settings, settings_attribute, "") if settings else ""
        if not token:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Internal API is not configured",
            )
        if not x_internal_token or not hmac.compare_digest(
            x_internal_token.encode("utf-8"),
            token.encode("utf-8"),
        ):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid internal service token",
            )

    return guard
