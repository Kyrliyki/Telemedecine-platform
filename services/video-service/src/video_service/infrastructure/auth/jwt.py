"""Проверка JWT, выпущенных AuthService (OAuth2 Resource Server)."""

from __future__ import annotations

import logging

import jwt
from common_auth import (
    JwtValidator,
    RevocationStoreUnavailableError,
    TokenRevocationChecker,
    TokenRevokedError,
)

from video_service.application.dto import Actor
from video_service.application.ports import InvalidTokenError
from video_service.domain.enums import ActorRole

logger = logging.getLogger(__name__)


class JwtVerifier:
    def __init__(
        self,
        *,
        secret: str,
        algorithms: list[str],
        audience: str | None = None,
        issuer: str | None = None,
        public_key: str = "",
        jwks_url: str = "",
        auth_redis_url: str = "",
        revocation_enabled: bool = False,
    ) -> None:
        self._validator = JwtValidator(
            issuer=issuer or "auth-service",
            audience=audience or "telemedicine",
            jwk_set_uri=jwks_url or None,
            public_key=public_key.replace("\\n", "\n").strip() or (secret or None),
            algorithms=algorithms,
        )
        self._revocation = (
            TokenRevocationChecker(auth_redis_url)
            if revocation_enabled and auth_redis_url
            else None
        )

    async def verify(self, token: str) -> Actor:
        try:
            payload = self._validator.decode(token)
        except jwt.PyJWTError as exc:
            raise InvalidTokenError(str(exc)) from exc

        if self._revocation is not None:
            try:
                await self._revocation.ensure_active(
                    subject=payload.subject,
                    jti=payload.jti,
                    issued_at_epoch=payload.issued_at.timestamp(),
                )
            except TokenRevokedError as exc:
                raise InvalidTokenError("Токен отозван") from exc
            except RevocationStoreUnavailableError as exc:
                raise InvalidTokenError("Хранилище отзыва токенов недоступно") from exc

        return Actor(
            identity=payload.subject,
            roles=_extract_roles(payload.roles),
            display_name=_extract_name(payload.claims, payload.subject),
        )

    async def aclose(self) -> None:
        if self._revocation is not None:
            await self._revocation.aclose()


def _extract_roles(roles: frozenset[str]) -> frozenset[ActorRole]:
    parsed = {ActorRole.from_claim(value) for value in roles}
    return frozenset(role for role in parsed if role is not None)


def _extract_name(claims: dict, fallback: str) -> str:
    for key in ("name", "email", "preferred_username"):
        value = claims.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return fallback
