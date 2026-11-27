"""JWT dependency for the notification inbox."""

from __future__ import annotations

from common_auth import JwtAuth, JwtValidator, TokenRevocationChecker

from notification_service.config import Settings


def build_jwt_auth(settings: Settings) -> JwtAuth:
    """Use RS256/JWKS as the primary mode and keep a dev HS256 fallback."""

    if settings.jwt_algorithm.upper() == "HS256":
        if not settings.jwt_secret:
            raise ValueError("JWT_SECRET is required for HS256")
        validator = JwtValidator(
            issuer=settings.jwt_issuer,
            audience=settings.jwt_audience,
            public_key=settings.jwt_secret,
            algorithms=["HS256"],
            leeway_seconds=settings.jwt_leeway_seconds,
        )
    else:
        if not settings.jwks_url:
            raise ValueError("JWKS_URL is required for RS256")
        validator = JwtValidator(
            issuer=settings.jwt_issuer,
            audience=settings.jwt_audience,
            jwk_set_uri=settings.jwks_url,
            algorithms=[settings.jwt_algorithm],
            leeway_seconds=settings.jwt_leeway_seconds,
        )
    revocation = (
        TokenRevocationChecker(settings.auth_redis_url)
        if settings.auth_revocation_enabled
        else None
    )
    return JwtAuth(validator, revocation)
