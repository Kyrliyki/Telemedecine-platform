"""Shared authentication helpers for telemedicine Python services."""

from .internal import (
    INTERNAL_TOKEN_HEADER,
    InternalTokenSettings,
    internal_token_guard,
)
from .revocation import (
    RevocationStoreUnavailableError,
    TokenRevocationChecker,
    TokenRevokedError,
)
from .security import (
    JwtAuth,
    JwtValidator,
    TokenPayload,
    bearer_scheme,
)
from .settings import JwtAuthSettings

__all__ = [
    "INTERNAL_TOKEN_HEADER",
    "InternalTokenSettings",
    "JwtAuth",
    "JwtAuthSettings",
    "JwtValidator",
    "RevocationStoreUnavailableError",
    "TokenPayload",
    "TokenRevocationChecker",
    "TokenRevokedError",
    "bearer_scheme",
    "internal_token_guard",
]
