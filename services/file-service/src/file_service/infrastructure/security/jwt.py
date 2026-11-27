"""JWT verification (tokens are issued by AuthService)."""

from __future__ import annotations

import logging
from uuid import UUID

import jwt
from common_auth import (
    JwtValidator,
    RevocationStoreUnavailableError,
    TokenRevocationChecker,
    TokenRevokedError,
)

from file_service.application.dto import Actor
from file_service.domain.exceptions import AuthenticationError
from file_service.infrastructure.config import Settings

logger = logging.getLogger(__name__)


class JwtVerifier:
    def __init__(self, settings: Settings) -> None:
        self._validator = JwtValidator(
            issuer=settings.jwt_issuer or "auth-service",
            audience=settings.jwt_audience or "telemedicine",
            jwk_set_uri=settings.jwt_jwks_url or None,
            public_key=(
                settings.jwt_public_key.replace("\\n", "\n").strip()
                or settings.jwt_secret
                or None
            ),
            algorithms=[settings.jwt_algorithm],
        )
        self._revocation = (
            TokenRevocationChecker(settings.auth_redis_url)
            if settings.auth_revocation_enabled
            else None
        )

    async def verify(self, token: str) -> Actor:
        try:
            payload = self._validator.decode(token)
        except jwt.PyJWTError as exc:
            logger.info("JWT validation failed", extra={"reason": str(exc)})
            raise AuthenticationError("Invalid or expired access token") from exc

        if self._revocation is not None:
            try:
                await self._revocation.ensure_active(
                    subject=payload.subject,
                    jti=payload.jti,
                    issued_at_epoch=payload.issued_at.timestamp(),
                )
            except TokenRevokedError as exc:
                raise AuthenticationError("Token has been revoked") from exc
            except RevocationStoreUnavailableError as exc:
                raise AuthenticationError("Token revocation store is unavailable") from exc

        try:
            actor_id = UUID(payload.subject)
        except ValueError as exc:
            raise AuthenticationError("Token subject is not a UUID") from exc

        roles = frozenset(role.lower() for role in payload.roles)
        return Actor(id=actor_id, roles=roles)

    async def aclose(self) -> None:
        if self._revocation is not None:
            await self._revocation.aclose()
