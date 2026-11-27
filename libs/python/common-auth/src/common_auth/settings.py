from __future__ import annotations

from pydantic import AliasChoices, Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class JwtAuthSettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="SECURITY_JWT_", extra="ignore")

    issuer: str = Field(
        default="auth-service",
        validation_alias=AliasChoices("SECURITY_JWT_ISSUER", "JWT_ISSUER"),
    )
    jwk_set_uri: str = Field(
        default="http://localhost:8080/oauth2/jwks",
        validation_alias=AliasChoices(
            "SECURITY_JWT_JWK_SET_URI",
            "SECURITY_JWT_JWKS_URL",
            "JWKS_URL",
        ),
    )
    
    public_key: str | None = Field(
        default=None,
        validation_alias=AliasChoices(
            "SECURITY_JWT_PUBLIC_KEY",
            "SECURITY_JWT_KEY",
        ),
    )
    audience: str = Field(
        default="telemedicine",
        validation_alias=AliasChoices("SECURITY_JWT_AUDIENCE", "JWT_AUDIENCE"),
    )
    algorithms: list[str] = Field(
        default_factory=lambda: ["RS256"],
        validation_alias=AliasChoices(
            "SECURITY_JWT_ALGORITHMS",
            "SECURITY_JWT_ALGORITHM",
        ),
    )
    leeway_seconds: int = 30

    @field_validator("algorithms", mode="before")
    @classmethod
    def _split_algorithms(cls, value: object) -> object:
        if isinstance(value, str):
            return [item.strip() for item in value.replace(",", " ").split() if item.strip()]
        return value
