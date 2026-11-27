"""JWT validation and FastAPI dependencies shared by Python services.

The AuthService issues RS256 tokens. Resource services only need the public
JWKS endpoint (``security.jwt.jwk-set-uri``) or a static public key.
"""

from __future__ import annotations

import datetime as dt
from collections.abc import Callable, Iterable
from dataclasses import dataclass, field
from typing import TYPE_CHECKING

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from .revocation import RevocationStoreUnavailableError, TokenRevocationChecker, TokenRevokedError

if TYPE_CHECKING:
    from .settings import JwtAuthSettings

bearer_scheme = HTTPBearer(auto_error=False)


@dataclass(frozen=True, slots=True)
class TokenPayload:
    """Validated access-token claims."""

    subject: str
    roles: frozenset[str]
    jti: str | None
    expires_at: dt.datetime
    issued_at: dt.datetime
    claims: dict = field(default_factory=dict)

    def has_role(self, role: str) -> bool:
        return role.upper() in self.roles

    def require_role(self, role: str) -> None:
        if not self.has_role(role):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Role {role} is required",
            )


class JwtValidator:
    """Validates AuthService access tokens (signature, issuer, audience, exp)."""

    def __init__(
        self,
        *,
        issuer: str,
        audience: str,
        jwk_set_uri: str | None = None,
        public_key: str | None = None,
        algorithms: Iterable[str] = ("RS256",),
        leeway_seconds: int = 30,
    ) -> None:
        if not issuer:
            raise ValueError("issuer is required")
        if not audience:
            raise ValueError("audience is required")
        if not jwk_set_uri and not public_key:
            raise ValueError("either jwk_set_uri or public_key is required")

        self._issuer = issuer
        self._audience = audience
        self._algorithms = tuple(algorithms)
        self._leeway = leeway_seconds
        self._public_key = public_key
        self._jwks_client = (
            jwt.PyJWKClient(jwk_set_uri, cache_keys=True) if jwk_set_uri is not None else None
        )

    @classmethod
    def from_settings(cls, settings: JwtAuthSettings) -> JwtValidator:
        if settings.public_key:
            return cls(
                issuer=settings.issuer,
                audience=settings.audience,
                public_key=settings.public_key,
                algorithms=settings.algorithms,
                leeway_seconds=settings.leeway_seconds,
            )
        return cls(
            issuer=settings.issuer,
            audience=settings.audience,
            jwk_set_uri=settings.jwk_set_uri or None,
            algorithms=settings.algorithms,
            leeway_seconds=settings.leeway_seconds,
        )

    def decode(self, token: str) -> TokenPayload:
        if not token or not token.strip():
            raise jwt.InvalidTokenError("empty token")

        key = self._public_key
        if self._jwks_client is not None:
            signing_key = self._jwks_client.get_signing_key_from_jwt(token)
            key = signing_key.key
        if key is None:
            raise jwt.InvalidTokenError("no signing key configured")

        claims = jwt.decode(
            token,
            key,
            algorithms=list(self._algorithms),
            audience=self._audience,
            issuer=self._issuer,
            leeway=self._leeway,
            options={"require": ["exp", "sub", "iss", "aud", "iat"]},
        )

        subject = claims.get("sub")
        expires_at = claims.get("exp")
        issued_at = claims.get("iat")
        if not subject or expires_at is None or issued_at is None:
            raise jwt.InvalidTokenError("missing sub, exp or iat claim")

        roles = frozenset(_extract_roles(claims))
        return TokenPayload(
            subject=subject,
            roles=roles,
            jti=claims.get("jti"),
            expires_at=dt.datetime.fromtimestamp(expires_at, tz=dt.UTC),
            issued_at=dt.datetime.fromtimestamp(issued_at, tz=dt.UTC),
            claims=dict(claims),
        )


def _extract_roles(claims: dict) -> set[str]:
    """Accept both the Java ``roles`` array and the dev ``role`` claim.

    The Java AuthService puts upper-case roles into ``roles``. The Python
    development issuer historically used a single lower-case ``role`` claim, so
    both shapes must validate during the migration.
    """

    values: list[str] = []

    raw_roles = claims.get("roles")
    if isinstance(raw_roles, list):
        values.extend(str(role) for role in raw_roles if isinstance(role, str))
    elif isinstance(raw_roles, str):
        values.extend(raw_roles.replace(",", " ").split())

    raw_role = claims.get("role")
    if isinstance(raw_role, str):
        values.append(raw_role)

    realm = claims.get("realm_access")
    if isinstance(realm, dict) and isinstance(realm.get("roles"), list):
        values.extend(str(role) for role in realm["roles"] if isinstance(role, str))

    scope = claims.get("scope")
    if isinstance(scope, str):
        values.extend(scope.replace(",", " ").split())

    return {value.strip().upper() for value in values if value and value.strip()}


class JwtAuth:
    """FastAPI dependency factory bound to a :class:`JwtValidator`."""

    def __init__(
        self,
        validator: JwtValidator,
        revocation: TokenRevocationChecker | None = None,
    ) -> None:
        self._validator = validator
        self._revocation = revocation

    async def __call__(
        self,
        credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    ) -> TokenPayload:
        if credentials is None or credentials.scheme.lower() != "bearer":
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Authentication required",
                headers={"WWW-Authenticate": "Bearer"},
            )
        try:
            payload = self._validator.decode(credentials.credentials)
        except jwt.PyJWTError as exc:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid or expired token",
                headers={"WWW-Authenticate": "Bearer"},
            ) from exc

        if self._revocation is not None:
            try:
                await self._revocation.ensure_active(
                    subject=payload.subject,
                    jti=payload.jti,
                    issued_at_epoch=payload.issued_at.timestamp(),
                )
            except TokenRevokedError as exc:
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="Token has been revoked",
                    headers={"WWW-Authenticate": "Bearer"},
                ) from exc
            except RevocationStoreUnavailableError as exc:
                raise HTTPException(
                    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                    detail="Token revocation store is unavailable",
                ) from exc

        return payload

    def require(self, *roles: str) -> Callable:
        required = frozenset(role.upper() for role in roles)

        async def dependency(
            user: TokenPayload = Depends(self),
        ) -> TokenPayload:
            if required and not required.intersection(user.roles):
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail="Insufficient permissions",
                )
            return user

        return dependency

    async def aclose(self) -> None:
        if self._revocation is not None:
            await self._revocation.aclose()
