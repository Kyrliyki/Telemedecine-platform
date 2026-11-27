"""Typed application settings (12-factor, ``APP_`` environment prefix)."""

from __future__ import annotations

from functools import lru_cache
from typing import Literal

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

DEFAULT_JWT_SECRET = "dev-secret-change-me-0123456789abcdef"

class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="APP_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    app_name: str = "appointment-service"
    version: str = "0.1.0"
    env: Literal["dev", "test", "prod"] = "dev"
    log_level: str = "INFO"

    
    database_url: str = "postgresql+psycopg://appointment:appointment@localhost:5432/appointment"
    
    
    db_schema: str = ""
    db_echo: bool = False
    db_pool_size: int = 5
    db_max_overflow: int = 10
    auto_migrate: bool = True
    alembic_ini: str = "alembic.ini"

    
    redis_url: str = "redis://localhost:6379/0"
    cache_enabled: bool = True
    cache_ttl_seconds: int = 60

    
    rabbitmq_url: str = "amqp://appointment:appointment@localhost:5672/"
    rabbitmq_exchange: str = "appointment.events"
    broker_enabled: bool = True
    outbox_poll_interval: float = 2.0
    outbox_batch_size: int = 100

    
    jwt_secret: str = DEFAULT_JWT_SECRET
    jwt_algorithm: str = "HS256"
    
    
    jwks_url: str = ""
    jwt_public_key: str = ""
    jwt_issuer: str = "auth-service"
    jwt_audience: str = "appointment-service"
    jwt_leeway_seconds: int = 10
    auth_redis_url: str = "redis://localhost:6379/0"
    auth_revocation_enabled: bool = True

    
    internal_service_token: str = ""
    schedule_service_url: str = ""
    user_service_url: str = ""

    
    cors_origins: str = "*"

    @property
    def is_dev(self) -> bool:
        return self.env in ("dev", "test")

    @property
    def cors_origin_list(self) -> list[str]:
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]

    @model_validator(mode="after")
    def _guard_production_defaults(self) -> Settings:
        if (
            self.env == "prod"
            and self.jwt_algorithm.upper() == "HS256"
            and self.jwt_secret == DEFAULT_JWT_SECRET
        ):
            raise ValueError("APP_JWT_SECRET must be changed in production")
        return self

@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
