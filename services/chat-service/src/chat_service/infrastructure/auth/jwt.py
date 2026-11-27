from __future__ import annotations

from functools import lru_cache

import jwt
from common_auth import (
    JwtValidator as CommonJwtValidator,
)
from common_auth import (
    RevocationStoreUnavailableError,
    TokenPayload,
    TokenRevocationChecker,
    TokenRevokedError,
)

from chat_service.config import get_settings


@lru_cache(maxsize=1)
def get_jwt_validator() -> JwtValidator:
    """One validator per process: keeps the JWKS cache warm."""
    return JwtValidator()


class JwtValidator:
    def __init__(self) -> None:
        s = get_settings()
        self._validator = CommonJwtValidator(
            issuer=s.jwt_issuer or "auth-service",
            audience=s.jwt_audience or "telemedicine",
            jwk_set_uri=s.jwt_jwks_url or None,
            public_key=s.jwt_public_key.replace("\\n", "\n").strip()
            or (s.jwt_secret or None),
            algorithms=[s.jwt_algorithm],
        )
        self._revocation = (
            TokenRevocationChecker(s.auth_redis_url) if s.auth_revocation_enabled else None
        )

    async def validate(self, token: str) -> TokenPayload:
        try:
            payload = self._validator.decode(token)
        except jwt.PyJWTError as error:
            raise ValueError(f"invalid token: {error}") from error

        if self._revocation is not None:
            try:
                await self._revocation.ensure_active(
                    subject=payload.subject,
                    jti=payload.jti,
                    issued_at_epoch=payload.issued_at.timestamp(),
                )
            except TokenRevokedError as error:
                raise ValueError("token has been revoked") from error
            except RevocationStoreUnavailableError as error:
                raise ValueError("token revocation store is unavailable") from error

        return payload

    async def aclose(self) -> None:
        if self._revocation is not None:
            await self._revocation.aclose()
