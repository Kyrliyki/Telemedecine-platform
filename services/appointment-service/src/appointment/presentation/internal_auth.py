"""Internal service-to-service guard (``X-Internal-Token``).

Kept local instead of importing ``common-auth`` because this service is built
from its own Docker context, which does not include the workspace libraries.
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
        expected = getattr(request.app.state.settings, "internal_service_token", "")
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
