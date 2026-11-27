from typing import Annotated
from uuid import UUID

from fastapi import Depends, Header, HTTPException

from chat_service.config import get_settings
from chat_service.infrastructure.auth.jwt import get_jwt_validator


async def current_user(
    authorization: Annotated[str | None, Header()] = None,
    x_test_user_id: Annotated[UUID | None, Header()] = None,
) -> UUID:
    settings = get_settings()

    
    if settings.auth_disabled:
        return x_test_user_id or settings.test_user_id

    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "missing bearer token")
    token = authorization.removeprefix("Bearer ").strip()
    try:
        payload = await get_jwt_validator().validate(token)
    except ValueError as e:
        raise HTTPException(401, str(e)) from e
    return UUID(payload.subject)

CurrentUser = Annotated[UUID, Depends(current_user)]