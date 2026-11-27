"""Internal service-to-service guard (``X-Internal-Token``).

Local implementation because file-service is built from its own Docker context.
"""

from __future__ import annotations

import hmac

from fastapi import Header, HTTPException, Request, status

INTERNAL_TOKEN_HEADER = "X-Internal-Token"


def internal_token_guard():
    async def guard(
        request: Request,
        x_internal_token: str | None = Header(
            default=None,
            alias=INTERNAL_TOKEN_HEADER,
        ),
    ) -> None:
        settings = getattr(request.app.state, "settings", None)
        expected = getattr(settings, "internal_api_token", "") if settings else ""
        if not expected:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Internal API is not configured",
            )
        if not x_internal_token or not hmac.compare_digest(
            x_internal_token.encode("utf-8"),
            expected.encode("utf-8"),
        ):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid internal service token",
            )

    return guard
