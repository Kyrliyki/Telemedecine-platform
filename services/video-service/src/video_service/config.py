"""Конфигурация сервиса (12-factor, переменные окружения)."""

from __future__ import annotations

from functools import lru_cache
from typing import Literal

from pydantic import Field, SecretStr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    
    app_name: str = "VideoService"
    environment: Literal["local", "test", "staging", "production"] = "local"
    log_level: str = "INFO"
    docs_enabled: bool = True
    cors_origins: str = ""

    
    auth_enabled: bool = True
    jwt_secret: SecretStr = SecretStr("dev-secret-change-me")
    
    
    jwt_public_key: str = ""
    jwt_jwks_url: str = ""
    jwt_algorithm: str = "HS256"
    jwt_audience: str = ""
    jwt_issuer: str = ""

    
    livekit_url: str = "http://localhost:7880"
    livekit_public_url: str = "ws://localhost:7880"
    livekit_api_key: str = "devkey"
    livekit_api_secret: SecretStr = SecretStr("secret")
    livekit_token_ttl_seconds: int = Field(default=900, gt=0, le=86_400)
    livekit_room_empty_timeout_seconds: int = Field(default=300, ge=0)
    livekit_departure_timeout_seconds: int = Field(default=20, ge=0)

    
    appointment_service_url: str = ""
    internal_service_token: str = ""
    auth_redis_url: str = "redis://localhost:6379/0"
    auth_revocation_enabled: bool = True

    
    storage_backend: Literal["memory", "postgres"] = "memory"
    database_url: str = "postgresql+psycopg://video:video@localhost:5432/video"
    db_schema: str = ""
    database_echo: bool = False

    
    
    rabbitmq_url: str = ""
    rabbitmq_exchange: str = "video.events"

    
    
    
    redis_url: str = ""

    @field_validator("jwt_audience", "jwt_issuer", mode="before")
    @classmethod
    def _empty_to_none(cls, value: object) -> object:
        return value or ""

    @property
    def cors_origin_list(self) -> list[str]:
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]

    @property
    def jwt_audience_or_none(self) -> str | None:
        return self.jwt_audience or None

    @property
    def jwt_issuer_or_none(self) -> str | None:
        return self.jwt_issuer or None

    @property
    def livekit_api_secret_value(self) -> str:
        return self.livekit_api_secret.get_secret_value()

    @property
    def jwt_secret_value(self) -> str:
        return self.jwt_secret.get_secret_value()

@lru_cache
def get_settings() -> Settings:
    return Settings()
