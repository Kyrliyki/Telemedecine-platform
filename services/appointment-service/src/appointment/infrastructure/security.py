"""JWT verification for AuthService access tokens.

AuthService signs RS256 tokens. The service verifies them through JWKS (or a
static public key), then checks the Redis revocation store.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from uuid import UUID

import jwt
from common_auth import (
    JwtValidator,
    RevocationStoreUnavailableError,
    TokenRevocationChecker,
    TokenRevokedError,
)

from appointment.domain.access import Actor
from appointment.domain.enums import Role
from appointment.domain.errors import AuthBackendUnavailable, AuthenticationError
from appointment.infrastructure.config import Settings


@dataclass(frozen=True, slots=True)
class TokenClaims:
    subject: UUID
    roles: frozenset[Role]
    expires_at: datetime

    def to_actor(self) -> Actor:
        return Actor(id=self.subject, roles=self.roles)


class JwtAuthenticator:
    def __init__(self, settings: Settings) -> None:
        self._validator = JwtValidator(
            issuer=settings.jwt_issuer,
            audience=settings.jwt_audience,
            jwk_set_uri=settings.jwks_url or None,
            public_key=(
                settings.jwt_public_key.replace("\\n", "\n").strip()
                or (settings.jwt_secret if settings.jwt_algorithm.upper() == "HS256" else None)
            ),
            algorithms=[settings.jwt_algorithm],
            leeway_seconds=settings.jwt_leeway_seconds,
        )
        self._revocation = (
            TokenRevocationChecker(settings.auth_redis_url)
            if settings.auth_revocation_enabled
            else None
        )

    async def authenticate(self, token: str) -> TokenClaims:
        try:
            payload = self._validator.decode(token)
        except jwt.PyJWTError as exc:
            raise AuthenticationError("invalid or expired access token") from exc

        if self._revocation is not None:
            try:
                await self._revocation.ensure_active(
                    subject=payload.subject,
                    jti=payload.jti,
                    issued_at_epoch=payload.issued_at.timestamp(),
                )
            except TokenRevokedError as exc:
                raise AuthenticationError("access token has been revoked") from exc
            except RevocationStoreUnavailableError as exc:
                raise AuthBackendUnavailable("token revocation store is unavailable") from exc

        try:
            subject = UUID(payload.subject)
            roles = _parse_roles(payload.roles)
            expires_at = payload.expires_at
        except (TypeError, ValueError) as exc:
            raise AuthenticationError("access token has invalid claims") from exc

        return TokenClaims(subject=subject, roles=roles, expires_at=expires_at)

    async def aclose(self) -> None:
        if self._revocation is not None:
            await self._revocation.aclose()


def _parse_roles(roles: frozenset[str]) -> frozenset[Role]:
    supported = {role.value for role in Role}
    parsed = frozenset(Role(value.lower()) for value in roles if value.lower() in supported)
    if not parsed:
        raise ValueError("access token has no supported role")
    return parsed
